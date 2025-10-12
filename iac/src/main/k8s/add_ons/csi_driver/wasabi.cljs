(ns k8s.add-ons.csi-driver.wasabi
  (:require [configs :refer [cfg]]))

(def config
  {:stack [:secret :chart]
   :app-namespace "kube-system"
   :no-namespace true
   :app-name      "wasabi-csi"
   :chart-opts {:chart "csi-s3"
                :fetchOpts {:repo "https://yandex-cloud.github.io/k8s-csi-s3/charts"}
                :helm-values-fn #(clj->js {:controller {:enabled false
                                                        :existingSecret {:name "wasabi-csi-secrets"}
                                                        :node {:existingSecret {:name "wasabi-csi-secrets"}}}}
                                          
                                          #_:storageClass #_{:create true
                                                         :name "csi-s3-sc"
                                                         :singleBucket "pulumi-harbor"
                                                         :region "us-east-1"
                                                         :accessKeyID "something"
                                                         :secretAccessKey "something"
                                                         ;;:bucket "pulumi-harbor"
                                                         })}
   :secret-opts {:stringData {:accessKeyID (-> cfg :wasabiId)
                              :secretAccessKey (-> cfg :wasabiKey)
                              :endpoint "http://s3proxy.s3proxy.svc.cluster.local"}}
   :vault-load-yaml false})
