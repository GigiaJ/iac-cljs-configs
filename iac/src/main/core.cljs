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

(def deployment-stack (clj->js  {:projectName "hetzner-k3s"
                           :stackName "deployment"
                           :workDir "/home/jaggar/dotfiles/iac"
                           :program base/quick-deploy-services}))

(defn run []
  
  (p/let [_ (println "Deploying cluster") 

          core-stack  (.createOrSelectStack pulumi-auto/LocalWorkspace
                                            init-stack)
          _ (.setConfig core-stack "hetzner-k3s:sshKeyName" #js {:value (-> cfg :sshKeyName) :secret false})
          _ (.setConfig core-stack "hetzner-k3s:sshPersonalKeyName" #js {:value (-> cfg :sshPersonalKeyName) :secret false})
          _ (.setConfig core-stack "hcloud:token" #js {:value (-> cfg :hcloudToken) :secret true})
          _ (.setConfig core-stack "hetzner-k3s:privateKeySsh" #js {:value (-> cfg :privateKeySsh) :secret true})
          ;;core-result (.up core-stack #js {:onOutput println}) 

          ;; Checks for changes on the core and prevents deleting the app-stack needlessly.
          ;; Important for the Openbao vault as it is deployed here and configured on the app-stack generally
          core-preview-result (.preview core-stack #js {:onOutput println})
          core-change-summary (js->clj (.-changeSummary core-preview-result) :keywordize-keys true)
          core-result              (when (or (zero? (:delete core-change-summary 0))
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

          _ (p/delay 2000)
          shared-stack (.createOrSelectStack pulumi-auto/LocalWorkspace
                                             shared-platform-stack)
          _ (.setConfig shared-stack "hetzner-k3s:sshKeyName" #js {:value (-> cfg :sshKeyName) :secret false})
          _ (.setConfig shared-stack "hetzner-k3s:sshPersonalKeyName" #js {:value (-> cfg :sshPersonalKeyName) :secret false})
          _ (.setConfig shared-stack "hcloud:token" #js {:value (-> cfg :hcloudToken) :secret true})
          _ (.setConfig shared-stack "kubeconfig" #js {:value kubeconfig :secret true})
          _ (.setConfig shared-stack "vault:token" #js {:value vault-token :secret true})
          _ (.setConfig shared-stack "vault:address" #js {:value vault-address :secret true})
          _ (.setConfig shared-stack "hetzner-k3s:apiToken" #js {:value (-> cfg :apiToken) :secret true})

          shared-results (.up shared-stack #js {:onOutput println})
          shared-outputs (.outputs shared-stack)
          _ (println shared-outputs)

          _ (p/delay 3000)

          app-stack  (.createOrSelectStack pulumi-auto/LocalWorkspace
                                           deployment-stack)
          _ (.setConfig app-stack "hetzner-k3s:sshKeyName" #js {:value (-> cfg :sshKeyName) :secret false})
          _ (.setConfig app-stack "hetzner-k3s:sshPersonalKeyName" #js {:value (-> cfg :sshPersonalKeyName) :secret false})
          _ (.setConfig app-stack "hetzner-k3s:privateKeySsh" #js {:value (-> cfg :privateKeySsh) :secret true})
          _ (.setConfig app-stack "kubeconfig" #js {:value kubeconfig :secret true})
          _ (.setConfig app-stack "vault:token" #js {:value vault-token :secret true})
          _ (.setConfig app-stack "hcloud:token" #js {:value (-> cfg :hcloudToken) :secret true})
          _ (.setConfig app-stack "vault:address" #js {:value vault-address :secret true})
          _ (.setConfig app-stack "hetzner-k3s:apiToken" #js {:value (-> cfg :apiToken) :secret true})

          app-result (.up app-stack #js {:onOutput println})

          app-outputs (.outputs app-stack)
          _ (println app-outputs)
          _ (.kill port-forward)]
    "All stacks deployed and cleaned up successfully."))


(defn main! []
  (-> (run)
      (p/then #(println %))
      (p/catch #(println "An error occurred:" %))))

