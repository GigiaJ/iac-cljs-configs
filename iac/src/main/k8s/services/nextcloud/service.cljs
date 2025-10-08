(ns k8s.services.nextcloud.service
  (:require [utils.k8s :refer [make-transformer]]))

(def config
  {:stack [:vault-secrets :helm-fn :chart :ingress]
   :app-namespace "nextcloud"
   :app-name      "nextcloud"
   :chart-repo    "https://nextcloud.github.io/helm/"
   :image-port 8080
   :vault-load-yaml true
   :helm-values-fn (make-transformer
                    (fn [{:keys [app-name secrets]}]
                      (let [{:keys [host]} secrets]
                        [[["nextcloud" "host"] host]
                         [["nextcloud" "trustedDomains"] [host app-name]]])))
   :transformations (fn [args _opts]
                      (let [kind (get-in args [:resource :kind])]
                        (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                          (update-in args [:resource :metadata :annotations]
                                     #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                          args)))})