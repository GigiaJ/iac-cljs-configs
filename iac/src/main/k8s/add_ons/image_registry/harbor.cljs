(ns k8s.add-ons.image-registry.harbor)

(def config
  {:stack [:storage-class :vault-secrets :chart]
   :app-namespace "harbor"
   :app-name "harbor"
   :image-port 80
   :vault-load-yaml false
   :chart-opts {:fetchOpts {:repo "https://helm.goharbor.io"}
                :values {:externalURL '(str "https://" host)
                         :expose {:type "ingress"
                                  :tls {:enabled false}
                                  :ingress {:className "caddy"
                                            :hosts {:core 'host}}}
                         :harborAdminPassword 'admin-password
                         :secretKey 'secret-key
                         :database {:enabled true
                                    :internal {:password 'db-password}}
                         :postgresql {:auth {:postgresPassword 'db-password}}
                         :persistence {:enabled true
                                       :resourcePolicy "keep"
                                       :imageChartStorage {:type "s3"
                                                           :redirect {:disable true}
                                                           :delete {:enabled true}
                                                           :disableredirect true
                                                           :s3 {:region 'region
                                                                :bucket 'bucket
                                                                :secure false
                                                                :v4auth true
                                                                :accesskey 's3-access-key
                                                                :secretkey 's3-secret-key
                                                                :regionendpoint 'region-endpoint}}}
                         :core {:secret 'core-secret
                                :xsrfKey 'core-xrsf-key
                                :tokenKey 'core-token-key
                                :tokenCert 'core-token-cert}
                         :jobservice {:secret 'jobservice-secret}
                         :registry {:secret 'registry-secret
                                    :s3 {:region 'region
                                         :bucket 'bucket
                                         :secure false
                                         :forcepathstyle true
                                         :accesskey 's3-access-key
                                         :secretkey 's3-secret-key
                                         :regionendpoint 'region-endpoint}
                                    :upload_purging {:enabled true}
                                    :logLevel "debug"}}
                :transformations [(fn [args _opts]
                                    (let [kind (get-in args [:resource :kind])]
                                      (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                                        (update-in args [:resource :metadata :annotations]
                                                   #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                                        args)))]}
   :storage-class-opts {:provisioner "ru.yandex.s3.csi"
                        :parameters {"mounter" "geesefs"
                                     "bucket" "pulumi-harbor"
                                     "singleBucket" "pulumi-harbor"
                                     "region" "us-east-1"
                                     "accessKey" "something"
                                     "secretKey" "something"
                                     "accessKeyID" "something"
                                     "secretAccessKey" "something"
                                     "usePathStyle" "true"
                                     "insecureSkipVerify" "true"
                                     "options" "--memory-limit 1000 --dir-mode 0777 --file-mode 0666"
                                     "csi.storage.k8s.io/provisioner-secret-name" "wasabi-csi-secrets"
                                     "csi.storage.k8s.io/provisioner-secret-namespace" "kube-system"
                                     "csi.storage.k8s.io/node-publish-secret-name" "wasabi-csi-secrets"
                                     "csi.storage.k8s.io/node-publish-secret-namespace" "kube-system"
                                     "csi.storage.k8s.io/node-stage-secret-name" "wasabi-csi-secrets"
                                     "csi.storage.k8s.io/node-stage-secret-namespace" "kube-system"
                                     "csi.storage.k8s.io/controller-publish-secret-name" "wasabi-csi-secrets"
                                     "csi.storage.k8s.io/controller-publish-secret-namespace" "kube-system"}}})

