(ns deployments
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   [base :as base]
   [infra.dns :as dns]
   [k8s.services.nextcloud.service :as nextcloud-service]
   [k8s.services.mesite.service :as mesite-service]))


(defn app-list [stack-ref config provider]
  (let [vault-provider (new vault/Provider
                            "vault-provider"
                            (clj->js {:address (.getOutput stack-ref "vaultAddress")
                                      :token   (.getOutput stack-ref "vaultToken")}))
        cloudflare-result (dns/setup-dns config vault-provider)
        nextcloud-result (nextcloud-service/deploy provider vault-provider)
        mesite-result (mesite-service/deploy provider vault-provider)]
    {:nextcloud nextcloud-result
     :cloudflare cloudflare-result}))

(defn extended-exports [init] 
  (let [exports (base.build-exports init)
        app-outputs (get init :setup)]
    (assoc exports :nextcloudUrl (.apply app-outputs #(get-in % [:nextcloud :nextcloud-url])))))

(defn quick-deploy []
  (->
   (base/initialize app-list) 
   (extended-exports)
   (clj->js)))
