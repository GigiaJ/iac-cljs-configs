(ns k8s.add-ons.image-registry.harbor
  (:require [utils.k8s :refer [make-transformer]]))

(def config
  {:stack [:storage-class :vault-secrets :chart :ingress]
   :app-namespace "harbor"
   :app-name "harbor"
   :image-port 80
   :vault-load-yaml false
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
                                     "csi.storage.k8s.io/controller-publish-secret-namespace" "kube-system"}}

   :ingress-opts {:spec {:ingressClassName "caddy"
                         :rules [{:host 'host
                                  :http {:paths '(make-paths
                                                  {:paths ["/" "/c"]
                                                   :backend {:name (str app-name "-portal")
                                                             :port {:number image-port}}}
                                                  {:paths ["/api" "/v2" "/chartrepo" "/service"]
                                                   :backend {:name (str app-name "-core")
                                                             :port {:number image-port}}})}}]}}

   :chart-opts {:fetchOpts {:repo "https://helm.goharbor.io"}
                
                :values {:externalURL '(str "https://" host)
                         :expose {:ingress {:enabled true}
                                  :tls {:enabled true}}
                         :harborAdminPassword 'admin-password
                         :secretKey 'secret-key
                         :database {:enabled true
                                    :internal {:password 'db-password}}
                         :postgresql {:auth {:postgresPassword 'db-password}}
                         :persistence {:enabled true
                                       :resourcePolicy "keep"}
                         :registry {:storage {:type "s3"
                                              :s3 {:region 'region
                                                   :bucket 'bucket
                                                   :accessKey 's3-access-key
                                                   :secretKey 's3-secret-key
                                                   :regionendpoint 'region-endpoint}}}}
                :transformations [(fn [args _opts]
                  (let [kind (get-in args [:resource :kind])]
                    (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                      (update-in args [:resource :metadata :annotations]
                                 #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                      args)))]
                }})

