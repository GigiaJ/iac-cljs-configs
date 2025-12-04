(ns k8s.services.nextcloud.service)

(def config
  {:stack [:vault:prepare [:k8s :httproute :chart]]
   :app-namespace "nextcloud"
   :app-name      "nextcloud"
   :image-port 80
   :vault-load-yaml true
   :k8s:chart-opts {:repositoryOpts {:repo "https://nextcloud.github.io/helm/"}
                    :values
                    {:podAnnotations {"backup.velero.io/backup-volumes" "data"}
                     :trustedDomains ['host 'app-name]
                     :nextcloud {:username 'username
                                 :password 'password
                                 :host 'host
                                 :containerPort 80
                                 :persistence {:enabled true
                                               :storageClass "juicefs-sc"
                                               :accessMode "ReadWriteMany"
                                               :size "1Ti"}}
                     :service {:port 80}
                     :redis {:auth {:password 'redis-password}}
                     :externalDatabase {:enabled true
                                        :type "mysql"
                                        :host "nextcloud-db.nextcloud.svc.cluster.local"
                                        :database "nextcloud"
                                        :user 'username
                                        :password 'mariadb-password}
                     :internalDatabase {:enabled false}
                     :mariadb {:enabled false
                               :auth {:username 'username
                                      :password 'mariadb-password
                                      :rootPassword 'mariadb-root-password}
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
                                                           :pullPolicy "Always"}}}

                     :transformations (fn [args _opts]
                                        (let [kind (get-in args [:resource :kind])]
                                          (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                                            (update-in args [:resource :metadata :annotations]
                                                       #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                                            args)))}}
   :k8s:httproute-opts {:spec {::hostnames ['host]
                               :rules [{:matches [{:path {:type "PathPrefix" :value "/"}}]
                                        :filters [{:type "ResponseHeaderModifier"
                                                   :responseHeaderModifier
                                                   {:set [{:name "Content-Security-Policy"
                                                           :value "frame-src 'self' https://cinny.hampter.quest https://productive.chickensalad.quest https://gitea.chickensalad.quest;"}]
                                                    :remove ["X-Content-Security-Policy"]}}]

                                        :backendRefs [{:name "nextcloud"
                                                       :port 80}]}]}}})