(ns base
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   ["@pulumiverse/harbor" :as harbor]
   ["@pulumi/kubernetes" :as k8s]
   [utils.general :as general]
   [utils.providers :refer [provider-apply]]
   [infra.init :as infra]
   [service-registries :refer [base-service-registry shared-service-registry prepare-service-registry deployment-service-registry]]
   )
     (:require-macros [utils.general :refer [p->]]))
  

(defn extended-exports [init]
  (let [;;exports (base.build-exports init)
        app-outputs (get init :setup)]
    #_(assoc exports :nextcloudUrl (.apply app-outputs #(get-in % [:nextcloud :nextcloud-url])))))

(defn mod-apps [pulumi-cfg service-registry]
  "Scans the registry, builds all needed providers, and calls deploy."
  (provider-apply service-registry pulumi-cfg))


(defn mod-init [configs]
  (let [pulumi-cfg (pulumi/Config.)]
    (mod-apps pulumi-cfg configs)))

(defn mod-quick-deploy [configs exports]
  (->
   (mod-init configs)
   (exports)
   (clj->js)))

(defn quick-deploy-base []
  (base/mod-quick-deploy
   base-service-registry
   (fn [init]
     (let [kcfg (p-> init .-cluster "generic:execute" .-kubeconfig)
           vaultToken (p-> init .-openbao "generic:execute" "root-token")
           vaultAddress (p-> init .-openbao "generic:execute" .-address)]
       #js {:kubeconfig kcfg
            :vaultAddress vaultAddress
            :vaultToken vaultToken}))))

(defn quick-deploy-shared []
  (base/mod-quick-deploy
     shared-service-registry
     (fn [init]
       (let [secrets (p-> init .-harbor "vault:prepare" "stringData")]
         {:url (p-> secrets .-host #(str "https://" %))
          :username (p-> secrets .-username)
          :password (p-> secrets .-password)}))))



(defn quick-deploy-prepare []
  (base/mod-quick-deploy prepare-service-registry extended-exports))

(defn quick-deploy-services []
  (base/mod-quick-deploy deployment-service-registry extended-exports))