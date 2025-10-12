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

   :ingress-opts {:service-name "harbor-portal"}

   :chart-opts {:fetchOpts {:repo "https://helm.goharbor.io"}
                :transformations (fn [args _opts]
                                   (let [kind (get-in args [:resource :kind])]
                                     (if (some #{kind} ["StatefulSet" "PersistentVolumeClaim" "Ingress"])
                                       (update-in args [:resource :metadata :annotations]
                                                  #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
                                       args)))
                :helm-values-fn (make-transformer
                                 (fn [{:keys [app-name secrets]}]
                                   (let [{:keys [host secret-key admin-password db-password region bucket s3-access-key s3-secret-key region-endpoint]} secrets]
                                     [[["externalURL"] (str "https://" host)]
                                      [["expose" "ingress" "enabled"] false]
                                      [["expose" "tls" "enabled"] false]
                                      [["harborAdminPassword"] admin-password]
                                      [["secretKey"] secret-key]
                                      [["database" "enabled"] true]
                                      [["database" "internal" "password"] db-password]
                                      [["persistence" "enabled"] true]
                                      [["persistence" "resourcePolicy"] "keep"]
                                      [["registry" "storage" "type" "s3"]]
                                      [["registry" "storage" "s3" "region" region]]
                                      [["registry" "storage" "s3" "bucket" bucket]]
                                      [["registry" "storage" "s3" "accessKey" s3-access-key]]
                                      [["registry" "storage" "s3" "secretKey" s3-secret-key]] 
                                      [["registry" "storage" "s3" "regionendpoint" region-endpoint]]
                                      
                                      ;;[["persistence" "persistentVolumeClaim" "registry" "storageClass"] "harbor"]
                                      ;;[["persistence" "persistentVolumeClaim" "database" "storageClass"] "harbor"]
                                      ;;[["persistence" "persistentVolumeClaim" "jobservice" "storageClass"] "harbor"]
                                      ;;[["persistence" "persistentVolumeClaim" "redis" "storageClass"] "harbor"]
                                      ])))}})



(def ingress-options
  {:function-keys [:host :service-name :image-port]
   :ingress-rules [{:host 'host
                   :http {:paths [{:path "/"
                                   :pathType "Prefix"
                                   :backend {:service {:name (str 'service-name "-core")
                                                       :port {:number 'image-port}}}}]}}
                   {:host 'host
                    :http {:paths [{:path "/api"
                                    :pathType "Prefix"
                                    :backend {:service {:name (str 'service-name "-portal")
                                                        :port {:number 'image-port}}}}]}}]})
