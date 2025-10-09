(ns k8s.add-ons.csi-driver.wasabi
  (:require [configs :refer [cfg]]))

(defn wasabi-config []
  (pulumi/Config. "wasabi")) 

(def config
  (let [wasabi-id (-> cfg :wasabiId)
        wasabi-key (-> cfg :wasabiKey)
        wasabi-secret-name "wasabi-csi-secrets"
        wasabi-secret-namespace "kube-system"]
    {:stack [:secret :storage-class :chart]
     :app-namespace "kube-system"
     :app-name      "wasabi-csi"
     :chart-opts {:chart "csi-s3"
                  :fetchOpts {:repo "https://yandex-cloud.github.io/k8s-csi-s3/charts"}
                  :helm-values-fn #(clj->js {:controller {:enabled false
                                                          :existingSecret {:name wasabi-secret-name }
                                                          :node {:existingSecret {:name wasabi-secret-name }}}})}
     :storage-class-opts {:provisioner "ru.yandex.s3.csi"
                          :parameters {"endpoint" "https://s3.us-east-1.wasabisys.com"
                                       "region" "us-east-1"
                                       "bucket" "pulumi-csi-s3"
                                       "mounter" "geesefs"
                                       "csi.storage.k8s.io/provisioner-secret-name" wasabi-secret-name
                                       "csi.storage.k8s.io/provisioner-secret-namespace" wasabi-secret-namespace
                                       "csi.storage.k8s.io/node-publish-secret-name" wasabi-secret-name
                                       "csi.storage.k8s.io/node-publish-secret-namespace" wasabi-secret-namespace}}
     :secret-opts {:stringData {:accessKeyID wasabi-id
                                :secretAccessKey wasabi-key}}
     :vault-load-yaml false}))
