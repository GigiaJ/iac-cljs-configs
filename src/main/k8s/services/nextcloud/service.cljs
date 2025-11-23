(ns k8s.services.nextcloud.service)

(def config
  {:stack [:vault-secrets :chart :ingress]
   :app-namespace "nextcloud"
   :app-name      "nextcloud"
   :image-port 8080
   :vault-load-yaml true
   :chart-opts {:fetchOpts {:repo "https://nextcloud.github.io/helm/"}
                :values {:nextcloud {:host 'host
                                     :trustedDomains ['host 'app-name]}}
                :transformations (fn [args _opts]
                                   (let [kind (get-in args [:resource :kind])]
                                     (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                                       (update-in args [:resource :metadata :annotations]
                                                  #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                                       args)))}})