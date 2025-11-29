(ns k8s.services.nextcloud.service)

;; Need to automate set-up/restore
(def config
  {:stack [:vault-secrets :k8s:chart :k8s:httproute]
   :app-namespace "nextcloud"
   :app-name      "nextcloud"
   :image-port 8080
   :vault-load-yaml true
   :k8s:chart-opts {:repositoryOpts {:repo "https://nextcloud.github.io/helm/"}
                :values {:nextcloud {:host 'host
                                     :trustedDomains ['host 'app-name]}}
                :transformations (fn [args _opts]
                                   (let [kind (get-in args [:resource :kind])]
                                     (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                                       (update-in args [:resource :metadata :annotations]
                                                  #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                                       args)))}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}
   })