(ns core
  (:require
   ["@pulumi/kubernetes" :as k8s]
   [clojure.core.async :refer [go <!]]
   [clojure.core.async.interop :refer [<p!]]
   [infra.init :as init]
   [k8s.csi-driver.hetzner :as hetznercsi]
   [k8s.services.openbao.openbao :as vault]
   [k8s.services.nextcloud.nextcloud :as nextcloud]
   ))

(defn app-deployments [provider] 
     (let [
           nextcloud-result (nextcloud/deploy-nextcloud-app provider)
           vault-result (vault/deploy-vault provider)
           ] 
       {
        :nextcloud nextcloud-result
        :vault vault-result
        }
       ))



(defn main! []
  (let [cluster (init/create-cluster)
        app-outputs (.apply (get cluster :kubeconfig)
                            (fn [kc]
                              (js/Promise.
                               (fn [resolve _reject]
                                 (let [provider (k8s/Provider. "k8s-dynamic-provider" #js {:kubeconfig kc})]
                                   (hetznercsi/deploy-csi-driver provider)
                                   (resolve (app-deployments provider)))))))] 
    
    
(set! (.-exports js/module)
      #js {
           :kubeconfig (get cluster :kubeconfig)
           :masterIp (get cluster :masterIp)          
           :nextcloudUrl (.apply app-outputs #(get app-outputs :nextcloudUrl))})
    
  #_(set! (.-exports js/module)
          #js {:nextcloudUrl (.apply app-outputs (fn [outputs] (.-nextcloudUrl outputs)))})
  ))
