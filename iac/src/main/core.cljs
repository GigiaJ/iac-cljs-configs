(ns core
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   [infra.init :as init]
   [k8s.csi-driver.hetzner :as hetznercsi]
   [k8s.services.openbao.openbao :as vault]
   [k8s.services.nextcloud.nextcloud :as nextcloud]))


(defn app-list [provider vault-params]
  (let [nextcloud-result (nextcloud/deploy-nextcloud provider vault-params)]
    {:nextcloud nextcloud-result}))

(defn app-deployments
  "Deploy applications with proper dependency chain"
  [provider kubeconfig apps]
  (let [vault-result (vault/deploy-vault provider kubeconfig)
        vault-params {:address (aget vault-result "address") :token (aget vault-result "root_token") :vault-port-forward (aget vault-result "port_forward_manager")}
        app-results (if (nil? apps) {} (apps provider vault-params))
        ]
    (assoc app-results :vault vault-result)
    ))

(defn init! [apps] 
  (let [cluster (init/create-cluster) 
        setup (.apply (get cluster :kubeconfig)
                      (fn [kc]
                        (js/Promise.
                         (fn [resolve _reject]
                           (let [provider (new k8s/Provider
                                               "k8s-dynamic-provider"
                                               (clj->js {:kubeconfig kc}))] 
                             (hetznercsi/deploy-csi-driver provider)
                             (resolve (app-deployments provider kc apps)))))))]
    {cluster setup}
    ))


(defn main! []
  (let [init (init! app-list)
        cluster (get init :cluster)
        app-outputs (get init :setup)]
    
    (set! (.-exports js/module)
          (clj->js {
                    :kubeconfig (get cluster :kubeconfig)
                    :masterIp (get cluster :masterIp)
                    :workerDeIp (get cluster :workerDeIp)
                    :workerUsIp (get cluster :workerUsIp)

                    :vaultAddress (.apply app-outputs #(get-in % [:vault :address]))
                    :vaultToken (.apply app-outputs #(get-in % [:vault :root-token]))

                    :nextcloudUrl (.apply app-outputs
                                          (fn [outputs]
                                            (get-in outputs [:nextcloud :nextcloud-url])))}))))
