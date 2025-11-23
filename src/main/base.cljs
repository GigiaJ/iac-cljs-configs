(ns base
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   ["@pulumiverse/harbor" :as harbor]
   ["@pulumi/kubernetes" :as k8s]
   [utils.general :as general]
   [utils.providers :refer [provider-apply]]
   [infra.init :as infra]
   [service-registries :refer [base-resources-definition initialize-resources-definition shared-resources-definition preparation-resources-definition deployment-resources-definition]]
   )
     (:require-macros [utils.general :refer [p->]]))
  

(defn extended-exports [init]
  (let [;;exports (base.build-exports init)
        app-outputs (get init :setup)]
    #_(assoc exports :nextcloudUrl (.apply app-outputs #(get-in % [:nextcloud :nextcloud-url])))))

(defn mod-apps [pulumi-cfg resource-configs]
  "Scans the registry, builds all needed providers, and calls deploy."
  (provider-apply resource-configs pulumi-cfg))


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
   base-resources-definition
   (fn [init]
     (let [kcfg (p-> init .-cluster "generic:execute" .-kubeconfig)]
       #js {:kubeconfig kcfg}))))

(defn quick-deploy-init []
  (base/mod-quick-deploy
   initialize-resources-definition
   (fn [init]
     (let [vaultToken (p-> init .-openbao "generic:execute" "root-token")
           vaultAddress (p-> init .-openbao "generic:execute" .-address)]
       #js {:vaultAddress vaultAddress
            :vaultToken vaultToken}))))

(defn quick-deploy-shared []
  (base/mod-quick-deploy
     shared-resources-definition
     (fn [init]
       (let [secrets (p-> init .-harbor "vault:prepare" "stringData")]
         {:url (p-> secrets .-host #(str "https://" %))
          :username (p-> secrets .-username)
          :password (p-> secrets .-password)}))))



(defn quick-deploy-prepare []
  (base/mod-quick-deploy preparation-resources-definition extended-exports))

(defn quick-deploy-services []
  (base/mod-quick-deploy deployment-resources-definition extended-exports))