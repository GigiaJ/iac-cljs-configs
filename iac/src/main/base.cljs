(ns base
  (:require
   ["@pulumi/pulumi" :as pulumi] 
   ["@pulumi/kubernetes" :as k8s] 
   [infra.init :as infra]
   [k8s.add-ons.csi-driver.hetzner :as hetznercsi]
   [k8s.add-ons.cert-manager :as cert-manager]
   [k8s.services.traefik.service :as traefik] 
   [k8s.services.openbao.service :as vault-service]
   ))


(defn app-deployments
  "Deploy applications with proper dependency chain"
  [provider config kc apps]
  (let [vault-result (vault-service/deploy-vault provider)
        app-results (if (nil? apps) {} (apps config provider))]
    (assoc app-results :vault vault-result)))

(defn initialize [apps]
  (let [cfg  (pulumi/Config.)
        cluster (infra/create-cluster cfg)
        setup (.apply (get cluster :kubeconfig)
                      (fn [kc]
                        (js/Promise.
                         (fn [resolve _reject]
                           (let [provider (new k8s/Provider
                                               "k8s-dynamic-provider"
                                               (clj->js {:kubeconfig kc}))] 
                             (traefik/set-up-traefik provider)
                             (cert-manager/deploy provider)
                             (hetznercsi/deploy-csi-driver provider) 
                             (resolve
                              (if (nil? apps)
                                (app-deployments provider cfg kc nil)
                                (app-deployments provider cfg kc apps))))))))]
    {:cluster cluster :setup setup}))

(defn build-exports [init]
  (let [cluster (get init :cluster)
        app-outputs (get init :setup)]
    {:kubeconfig   (get cluster :kubeconfig)
     :masterIp     (get cluster :masterIp)
     :workerDeIp   (get cluster :workerDeIp)
     :workerUsIp   (get cluster :workerUsIp)
     :vaultAddress (pulumi/output (.apply app-outputs #(get-in % [:vault :address])))
     :vaultToken   (pulumi/output (.apply app-outputs #(get-in % [:vault :root-token])))
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