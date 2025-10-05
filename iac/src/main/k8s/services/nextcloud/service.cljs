(ns k8s.services.nextcloud.service
  (:require
   [utils.k8s :refer [ make-transformer deploy-stack]]))

(defn- add-skip-await-transformation [args _opts]
  (let [kind (get-in args [:resource :kind])]
    (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
      (update-in args [:resource :metadata :annotations]
                 #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
      args)))

(defn deploy
  "Deploy Nextcloud using direct vault connection info."
  [provider vault-provider]
  (let [nextcloud-values-transformer
        (make-transformer
         (fn [{:keys [app-name secrets]}]
           (let [{:keys [host]} secrets]
             [[["nextcloud" "host"] host]
              [["nextcloud" "trustedDomains"] [host app-name]]])))
        
        stack (deploy-stack
               :namespace :vault-secrets :helm-fn :chart :ingress
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