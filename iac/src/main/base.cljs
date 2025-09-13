(ns base
  (:require
   ["@pulumi/pulumi" :as pulumi] 
   ["@pulumi/kubernetes" :as k8s] 
   [promesa.core :as p]   
   [infra.init :as infra]
   [k8s.csi-driver.hetzner :as hetznercsi]
   [k8s.services.openbao.openbao :as vault]
   ))


(defn app-deployments
  "Deploy applications with proper dependency chain"
  [provider kubeconfig apps]
  (let [vault-result (vault/deploy-vault provider kubeconfig)
        vault-params {:address (aget vault-result "address") :token (aget vault-result "root_token") :vault-port-forward (aget vault-result "port_forward_manager")}
        app-results (if (nil? apps) {} (apps provider vault-params))]
    (assoc app-results :vault vault-result)))

(defn initialize [apps]
  (let [cluster (infra/create-cluster)
        setup (.apply (get cluster :kubeconfig)
                      (fn [kc]
                        (js/Promise.
                         (fn [resolve _reject]
                           (let [provider (new k8s/Provider
                                               "k8s-dynamic-provider"
                                               (clj->js {:kubeconfig kc}))]
                             (hetznercsi/deploy-csi-driver provider)
                             (resolve
                              (if (nil? apps) (app-deployments provider kc nil) (app-deployments provider kc apps))))))))]
    {:cluster cluster :setup setup}))

(defn build-exports [init]
  (let [cluster (get init :cluster)
        app-outputs (get init :setup)]
    {:kubeconfig   (get cluster :kubeconfig)
     :masterIp     (get cluster :masterIp)
     :workerDeIp   (get cluster :workerDeIp)
     :workerUsIp   (get cluster :workerUsIp)
     :vaultAddress (.apply app-outputs #(get-in % [:vault :address]))
     :vaultToken   (.apply app-outputs #(get-in % [:vault :root-token]))
     }))


(defn quick-deploy []
  (->
   (initialize nil)
   (build-exports)
   (clj->js)))

(defn deploy-core []
  (let [init (initialize nil)]
    (set! (.-exports js/module) 
          (clj->js (build-exports init))
          )
    ))