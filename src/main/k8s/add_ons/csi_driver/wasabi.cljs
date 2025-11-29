(ns k8s.add-ons.csi-driver.wasabi
  (:require [configs :refer [cfg]]))

(def config
  {:stack [:k8s:secret :k8s:chart]
   :app-namespace "kube-system"
   :no-namespace true
   :app-name      "wasabi-csi"
   :k8s:chart-opts {:chart "csi-s3"
                :repositoryOpts {:repo "https://yandex-cloud.github.io/k8s-csi-s3/charts"}
                :values {:controller {:enabled false
                                                        :existingSecret {:name "wasabi-csi-secrets"}
                                                        :node {:existingSecret {:name "wasabi-csi-secrets"}}}}}
   :k8s:secret-opts {:stringData {:accessKeyID (-> cfg :wasabiId)
                             :secretAccessKey (-> cfg :wasabiKey)
                             :endpoint "http://wasabi-proxy.wasabi-proxy.svc.cluster.local"}}})