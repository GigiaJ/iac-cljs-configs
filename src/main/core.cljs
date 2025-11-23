(ns core
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/pulumi/automation" :as pulumi-auto]
   ["child_process" :as cp]
   [promesa.core :as p]
   [base :as base]
   [configs :refer [cfg]]))


(def base-stack (clj->js  {:projectName "hetzner-k3s"
                           :stackName "base"
                           :workDir "/home/jaggar/dotfiles/iac"
                           :program base/quick-deploy-base}))

(def init-stack (clj->js  {:projectName "hetzner-k3s"
                           :stackName "init"
                           :workDir "/home/jaggar/dotfiles/iac"
                           :program base/quick-deploy-init}))

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

(defn deploy-stack
  ([stack-definition configs]
   (deploy-stack stack-definition configs 0))

  ([stack-definition configs post-delay]
   (p/let
    [stack (.createOrSelectStack pulumi-auto/LocalWorkspace stack-definition)
     _     (p/doseq [config configs]
             (.setConfig stack (:name config)  (clj->js (dissoc config :name))))
     _     (.up stack #js {:onOutput println})
     outputs (.outputs stack)
     _     (p/delay post-delay)]
     outputs)))

(defn run []
  (p/let [_ (println "Deploying cluster")
          base-outputs (deploy-stack base-stack [{:name "hetzner-k3s:sshKeyName" :value (-> cfg :sshKeyName) :secret false}
                                                 {:name "hetzner-k3s:sshPersonalKeyName" :value (-> cfg :sshPersonalKeyName) :secret false}
                                                 {:name "hcloud:token" :value (-> cfg :hcloudToken) :secret true}
                                                 {:name "hetzner-k3s:privateKeySsh" :value (-> cfg :privateKeySsh) :secret true}])

          reused-configs [{:name "kubeconfig" :value (-> base-outputs (aget "kubeconfig") (.-value)) :secret true}]

          init-outputs (deploy-stack init-stack reused-configs 1000)
          port-forward (cp/spawn "kubectl"
                                 #js ["port-forward"
                                      "svc/openbao"
                                      "8200:8200"
                                      "-n"
                                      "vault"])

          reused-configs (conj reused-configs {:name "vault:token" :value (-> init-outputs (aget "vaultToken") (.-value)) :secret true})
          reused-configs (conj reused-configs {:name "vault:address" :value (-> init-outputs (aget "vaultAddress") (.-value)) :secret true})

          shared-outputs (deploy-stack shared-platform-stack 
                                       (conj reused-configs {:name "hetzner-k3s:apiToken" :value (-> cfg :apiToken) :secret true})
                                       1000)
          prepare-outputs (deploy-stack prepare-deployment-stack reused-configs 3000)
          deployment-outputs (deploy-stack deployment-stack reused-configs 2000)

          _ (.kill port-forward)]
    "All stacks deployed and cleaned up successfully."))


(defn main! []
  (-> (run)
      (p/then #(println %))
      (p/catch #(println "An error occurred:" %))))

;; Checks for changes on the core and prevents deleting the app-stack needlessly.
          ;; Important for the Openbao vault as it is deployed here and configured on the app-stack generally
          ;;core-preview-result (.preview core-stack #js {:onOutput println})
          ;;core-change-summary (js->clj (.-changeSummary core-preview-result) :keywordize-keys true)
          #_core-result              #_(when (or (zero? (:delete core-change-summary 0))
                                                 (pos? (:update core-change-summary 0))
                                                 (pos? (:create core-change-summary 0)))
                                         (.up core-stack #js {:onOutput println}))

(defn config-core [stack kubeconfig vault-token vault-address]
  (p/do
    ;;(.setConfig stack "hetzner-k3s:sshKeyName" #js {:value (-> cfg :sshKeyName) :secret false})
    ;;(.setConfig stack "hetzner-k3s:sshPersonalKeyName" #js {:value (-> cfg :sshPersonalKeyName) :secret false})
    ;;(.setConfig stack "hetzner-k3s:privateKeySsh" #js {:value (-> cfg :privateKeySsh) :secret true})
    (.setConfig stack "kubeconfig" #js {:value kubeconfig :secret true})
    (.setConfig stack "vault:token" #js {:value vault-token :secret true})
    ;;(.setConfig stack "hcloud:token" #js {:value (-> cfg :hcloudToken) :secret true})
    (.setConfig stack "vault:address" #js {:value vault-address :secret true})
    ;;(.setConfig stack "hetzner-k3s:apiToken" #js {:value (-> cfg :apiToken) :secret true})
    ))
