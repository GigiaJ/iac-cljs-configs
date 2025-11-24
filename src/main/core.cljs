(ns core
  (:require
   ["@pulumi/pulumi/automation" :as pulumi-auto]
   ["child_process" :as cp]
   [promesa.core :as p]
   [configs :refer [cfg]]
   [pulumicljs.execution.general :as general]
   [pulumicljs.execution.providers :refer [execute]]
   [stack-resource-definitions :refer [base-resources-definition 
                                       initialize-resources-definition
                                       shared-resources-definition
                                       preparation-resources-definition
                                       deployment-resources-definition]]
   )
  (:require-macros [pulumicljs.execution.general :refer [p->]]))


(defn define-stack [project-name stack-name work-dir program]
  (clj->js  {:projectName project-name
             :stackName stack-name
             :workDir work-dir
             :program program}))


(def base-stack
  (define-stack
    "hetzner-k3s"
    "base"
    "/home/jaggar/dotfiles/iac"
    (execute
     base-resources-definition
     (fn [output] (let [_ (js/console.log output)]
                    #js {:kubeconfig (p-> output .-cluster "generic:execute" .-kubeconfig)})))))

(def init-stack
  (define-stack 
    "hetzner-k3s"
    "init"
    "/home/jaggar/dotfiles/iac"
    (execute
     initialize-resources-definition
     (fn [output] #js {:vaultAddress (p-> output .-openbao "generic:execute" .-address)
                       :vaultToken (p-> output .-openbao "generic:execute" "root-token")}))))

(def shared-platform-stack
  (define-stack
    "hetzner-k3s"
    "shared"
    "/home/jaggar/dotfiles/iac"
    (execute
     shared-resources-definition
     (fn [output] (let [secrets (p-> output .-harbor "vault:prepare" "stringData")]
        #js {:url (p-> secrets .-host (fn [x] (str "https://" x)))
             :username (p-> secrets .-username)
             :password (p-> secrets .-password)})))))

(def prepare-deployment-stack
  (define-stack
    "hetzner-k3s"
    "prepare"
    "/home/jaggar/dotfiles/iac"
    (execute preparation-resources-definition (fn [output] {}))))

(def deployment-stack
  (define-stack
    "hetzner-k3s"
    "deployment"
    "/home/jaggar/dotfiles/iac"
    (execute deployment-resources-definition (fn [output] {}))))


(defn deploy-stack
  ([stack-definition inputs]
   (deploy-stack stack-definition inputs 0))

  ([stack-definition inputs post-delay]
   (p/let
    [stack (.createOrSelectStack pulumi-auto/LocalWorkspace stack-definition)
     _     (p/doseq [input inputs]
             (.setConfig stack (:name input)  (clj->js (dissoc input :name))))
     _     (.up stack #js {:onOutput println})
     outputs (.outputs stack)
     _     (p/delay post-delay)]
     outputs)))




(defn run []
  (p/let [_ (println "Deploying cluster")
          base-outputs (deploy-stack
                        base-stack
                        [{:name "hetzner-k3s:sshKeyName" :value (-> cfg :sshKeyName) :secret false}
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


;; Combo later w/ a reader to make a dynamic *stack* config
#_(defn test-stack [inputs project-name config-declarations outputs]
  {:pulumi-stack (clj->js  {:projectName project-name
                            :stackName stack-name
                            :workDir work-dir
                            :program #(execute config-declarations outputs)})
   :inputs inputs
   :outputs outputs})

#_(define-stack
  [{:name "hetzner-k3s:sshKeyName" :value (-> cfg :sshKeyName) :secret false}
   {:name "hetzner-k3s:sshPersonalKeyName" :value (-> cfg :sshPersonalKeyName) :secret false}
   {:name "hcloud:token" :value (-> cfg :hcloudToken) :secret true}
   {:name "hetzner-k3s:privateKeySsh" :value (-> cfg :privateKeySsh) :secret true}]
  "base"
  base-resources-definition
  #(#js {:kubeconfig (p-> % .-cluster "generic:execute" .-kubeconfig)}))


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
