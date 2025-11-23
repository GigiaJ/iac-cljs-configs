(ns k8s.add-ons.minio)

(def config
  {:stack [:vault-secrets :deployment :service :ingress]
   :app-namespace "minio"
   :no-namespace true
   :app-name      "minio"
   :image-port     9000
   :image   "quay.io/minio/minio"
   :load-yaml false
   :deployment-opts {:spec {:template {:spec {:containers [{:name "minio"
                                                            :args ["gateway" "s3"]
                                                            :env [{:name "MINIO_ROOT_USER" :valueFrom {:secretKeyRef {:name "minio-secrets"
                                                                                                                      :key "MINIO_ROOT_USER"}}}
                                                                  {:name "MINIO_ROOT_PASSWORD" :valueFrom {:secretKeyRef {:name "minio-secrets"
                                                                                                                          :key "MINIO_ROOT_PASSWORD"}}}
                                                                  {:name "MINIO_COMPAT"
                                                                   :value "on"}
                                                                  {:name "MINIO_S3_URL"
                                                                   :value "https://s3.wasabisys.com"}
                                                                  {:name "MINIO_ACCESS_KEY"
                                                                   :valueFrom {:secretKeyRef {:name "minio-secrets"
                                                                                              :key "MINIO_ACCESS_KEY"}}}
                                                                  {:name "MINIO_SECRET_KEY"
                                                                   :valueFrom {:secretKeyRef {:name "minio-secrets"
                                                                                              :key "MINIO_SECRET_KEY"}}}]}]}}
                            :nodeSelector {"kubernetes.io/hostname" "master-de"}}}})