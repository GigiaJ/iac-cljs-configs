(ns k8s.services.nextcloud.service
  (:require
   ["@pulumi/pulumi" :as pulumi]
   [utils.vault :as vault-utils]
   [utils.k8s :as k8s-utils]))

(defn- add-skip-await-transformation [args _opts]
  (let [kind (get-in args [:resource :kind])]
    (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
      (update-in args [:resource :metadata :annotations]
                 #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
      args)))


(defn deploy
  "Deploy Nextcloud using direct vault connection info."
  [provider vault-provider]
  (let [nextcloud-values-transformer (fn [{:keys [base-values hostname app-name]}]
                                       (-> base-values
                                           (assoc-in [:ingress :enabled] false)
                                           (assoc-in [:nextcloud :host] hostname)
                                           (assoc-in [:nextcloud :trusted_domains] [hostname app-name])))
        stack (k8s-utils/deploy-stack
               :namespace :vault-secrets :hostname :chart :ingress
               {:provider provider
                :vault-provider vault-provider
                :app-namespace "nextcloud"
                :app-name "nextcloud"
                :image-port 8080
                :vault-load-yaml true
                :chart-repo "https://nextcloud.github.io/helm/"
                :helm-values-fn nextcloud-values-transformer
                :transformations add-skip-await-transformation})]
    {:stack stack}))