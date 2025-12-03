(ns k8s.services.nextcloud.service)

(def config
  {:stack [:vault:prepare :k8s:chart :k8s:httproute]
   :app-namespace "nextcloud"
   :app-name      "nextcloud"
   :image-port 80
   :vault-load-yaml true
   :k8s:chart-opts {:repositoryOpts {:repo "https://nextcloud.github.io/helm/"}
                    :values
                    {:podAnnotations {"backup.velero.io/backup-volumes" "data"}
                     :nextcloud {:host 'host
                                 :containerPort 80
                                 :trustedDomains ['host 'app-name]
                                 :persistence {:enabled true
                                               :storageClass "juicefs-sc"
                                               :accessMode "ReadWriteMany"
                                               :size "1Ti"}}
                     :service {:port 80}
                     :mariadb {:enabled true 
                               :architecture "standalone"
                               :primary {:podAnnotations {"backup.velero.io/backup-volumes" "data"}
                                         :persistence {:enabled true
                                                       :storageClass "hcloud-volumes"
                                                       :size "8Gi"}}
                               ;; Obligatory what the fuck Broadcom, why are you like this. RIP Bitnami
                               :volumePermissions {:enabled true
                                                   :image {:registry "docker.io"
                                                           :repository "bitnami/os-shell"
                                                           :tag "latest"
                                                           :pullPolicy "Always"}}
                               }

                     :transformations (fn [args _opts]
                                        (let [kind (get-in args [:resource :kind])]
                                          (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                                            (update-in args [:resource :metadata :annotations]
                                                       #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                                            args)))}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})