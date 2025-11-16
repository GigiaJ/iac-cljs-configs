(ns core
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/pulumi/automation" :as pulumi-auto]
   ["child_process" :as cp]
   [promesa.core :as p]
   [base :as base]
   [configs :refer [cfg]]))


(def init-stack (clj->js  {:projectName "hetzner-k3s"
                           :stackName "init"
                           :workDir "/home/jaggar/dotfiles/iac"
                           :program base/quick-deploy-base}))

(def shared-platform-stack (clj->js  {:projectName "hetzner-k3s"
                                      :stackName "shared"
                                      :workDir "/home/jaggar/dotfiles/iac"
                                      :program base/quick-deploy-shared}))

(def prepare-deployment-stack (clj->js  {:projectName "hetzner-k3s"
                                      :stackName "prepare"
                                      :workDir "/home/jaggar/dotfiles/iac"
                                      :program base/quick-deploy-prepare}))

(def deployment-stack (clj->js  {:projectName "hetzner-k3s"
                                 :stackName "deployment"
                                 :workDir "/home/jaggar/dotfiles/iac"
                                 :program base/quick-deploy-services}))

(defn config-core [stack kubeconfig vault-token vault-address]
  (p/do (.setConfig stack "hetzner-k3s:sshKeyName" #js {:value (-> cfg :sshKeyName) :secret false})
        (.setConfig stack "hetzner-k3s:sshPersonalKeyName" #js {:value (-> cfg :sshPersonalKeyName) :secret false})
        (.setConfig stack "hetzner-k3s:privateKeySsh" #js {:value (-> cfg :privateKeySsh) :secret true})
        (.setConfig stack "kubeconfig" #js {:value kubeconfig :secret true})
        (.setConfig stack "vault:token" #js {:value vault-token :secret true})
        (.setConfig stack "hcloud:token" #js {:value (-> cfg :hcloudToken) :secret true})
        (.setConfig stack "vault:address" #js {:value vault-address :secret true})
        (.setConfig stack "hetzner-k3s:apiToken" #js {:value (-> cfg :apiToken) :secret true})))

(defn run []

  (p/let [_ (println "Deploying cluster")

          core-stack  (.createOrSelectStack pulumi-auto/LocalWorkspace
                                            init-stack)
          _ (.setConfig core-stack "hetzner-k3s:sshKeyName" #js {:value (-> cfg :sshKeyName) :secret false})
          _ (.setConfig core-stack "hetzner-k3s:sshPersonalKeyName" #js {:value (-> cfg :sshPersonalKeyName) :secret false})
          _ (.setConfig core-stack "hcloud:token" #js {:value (-> cfg :hcloudToken) :secret true})
          _ (.setConfig core-stack "hetzner-k3s:privateKeySsh" #js {:value (-> cfg :privateKeySsh) :secret true})
          core-result (.up core-stack #js {:onOutput println})

          ;; Checks for changes on the core and prevents deleting the app-stack needlessly.
          ;; Important for the Openbao vault as it is deployed here and configured on the app-stack generally
          ;;core-preview-result (.preview core-stack #js {:onOutput println})
          ;;core-change-summary (js->clj (.-changeSummary core-preview-result) :keywordize-keys true)
          #_core-result              #_(when (or (zero? (:delete core-change-summary 0))
                                                 (pos? (:update core-change-summary 0))
                                                 (pos? (:create core-change-summary 0)))
                                         (.up core-stack #js {:onOutput println}))
          core-outputs (.outputs core-stack)

          vault-address (-> core-outputs (aget "vaultAddress") (.-value))
          vault-token   (-> core-outputs (aget "vaultToken") (.-value))
          kubeconfig    (-> core-outputs (aget "kubeconfig") (.-value))

          _ (println core-outputs)


          _ (p/delay 2000)
          port-forward (cp/spawn "kubectl"
                                 #js ["port-forward"
                                      "svc/openbao"
                                      "8200:8200"
                                      "-n"
                                      "vault"])

          _ (p/delay 3000)

          shared-stack (.createOrSelectStack pulumi-auto/LocalWorkspace shared-platform-stack)
          _ (config-core shared-stack kubeconfig vault-token vault-address)

          shared-results (.up shared-stack #js {:onOutput println})
          shared-outputs (.outputs shared-stack)
          _ (println shared-outputs)

          _ (p/delay 2000)
          prepare-stack (.createOrSelectStack pulumi-auto/LocalWorkspace
                                              prepare-deployment-stack)
          _ (config-core prepare-stack kubeconfig vault-token vault-address)

          prepare-results (.up prepare-stack #js {:onOutput println})
          prepare-outputs (.outputs prepare-stack)
          _ (println prepare-outputs)

          _ (p/delay 3000)

          app-stack  (.createOrSelectStack pulumi-auto/LocalWorkspace
                                           deployment-stack)

          _ (config-core app-stack kubeconfig vault-token vault-address)

          app-result (.up app-stack #js {:onOutput println})

          app-outputs (.outputs app-stack)
          _ (println app-outputs)
          _ (.kill port-forward)]
    "All stacks deployed and cleaned up successfully."))


(defn main! []
  (-> (run)
      (p/then #(println %))
      (p/catch #(println "An error occurred:" %))))

