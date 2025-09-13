(ns core
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/pulumi/automation" :as pulumi-auto]
   ["child_process" :as cp]
   [promesa.core :as p]
   [base :as base]
   [configs :refer [cfg]]
   [k8s.services.nextcloud.nextcloud :as nextcloud]
   [deployments :as deployments]))


(def init-stack (clj->js  {:projectName "hetzner-k3s"
                       :stackName "cluster"
                       :workDir "/home/jaggar/dotfiles/iac"
                       :program base/quick-deploy}))

(defn run []
  (p/let [
          _ (println "Deploying cluster")
          core-stack  (.createOrSelectStack pulumi-auto/LocalWorkspace
                                            init-stack)
          _ (.setConfig core-stack "hetzner-k3s:sshKeyName" #js {:value (-> cfg :sshKeyName) :secret false}) 
          _ (.setConfig core-stack "hetzner-k3s:sshPersonalKeyName" #js {:value (-> cfg :sshPersonalKeyName) :secret false})
          _ (.setConfig core-stack "hcloud:token" #js {:value (-> cfg :hcloudToken) :secret true})
          _ (.setConfig core-stack "hetzner-k3s:privateKeySsh" #js {:value (-> cfg :privateKeySsh) :secret true})
          _ (println "Check1?")
          up-result (.up core-stack #js {:onOutput println})
          _ (println "Check2?")

          outputs (.outputs core-stack)
          _ (println outputs)
         ;; service-name (-> outputs (aget "serviceName") (aget "value"))
         ;; namespace (-> outputs (aget "namespace") (aget "value"))
         ;; _ (println (str "-> Service Name: " service-name ", Namespace: " namespace))

          ;; Start port-forward
         ;; _ (println "Starting kubectl port-forward...")
          ;;port-forward (cp/spawn "kubectl"
            ;;                     #js ["port-forward"
              ;;                        (str "svc/" service-name)
                ;;                      "8080:80"
                  ;;                    "-n"
                    ;;                  namespace])
        ;;  _ (p/delay 3000)

          ;; Deploy Stack B
         ;; _ (println "Deploying application stack")
          ;;app-stack (p/await (pulumi-auto/LocalWorkspace.createOrSelectStack
            ;;                  #js {:stackName "hetzner-k3s-cluster"
              ;;                     :projectName ""
                ;;                   :program #(p/promise (deployments/deploy-services))}))
         ;; _ (p/await (.preview app-stack))
          ;; _ (println "Application stack deployment complete.")
          
          ;; Clean up
          ;;_ (.kill port-forward)
         ;; _ (println "Cleaned up port-forward process.")
          ]

    ;; This final value is returned when the p/let chain completes
    "All stacks deployed and cleaned up successfully."))


(defn main! []
  (-> (run)
      (p/then #(println %))
      (p/catch #(println "An error occurred:" %))))

