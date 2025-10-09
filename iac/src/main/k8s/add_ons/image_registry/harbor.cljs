(ns k8s.add-ons.image-registry.harbor
  (:require [utils.k8s :refer [make-transformer]]))

(def config
  {:stack [:vault-secrets :chart :ingress]
   :app-namespace "harbor"
   :app-name "harbor"
   :image-port 80
   :vault-load-yaml false
   :chart-opts {:fetchOpts {:repo "https://helm.goharbor.io"}
                :helm-values-fn (make-transformer
                                 (fn [{:keys [app-name secrets]}]
                                   (let [{:keys [host secret-key admin-password db-password]} secrets]
                                     [[["externalURL"] (str "https://" host)]
                                      [["expose" "ingress" "enabled"] false]
                                      [["expose" "tls" "enabled"] false]
                                      [["harborAdminPassword"] admin-password]
                                      [["secretKey"] secret-key]
                                      [["database" "enabled"] true]
                                      [["database" "internal" "password"] db-password]
                                      [["persistence" "enabled"] true]
                                      [["persistence" "resourcePolicy"] "keep"]
                                      [["persistence" "persistentVolumeClaim" "registry" "storageClass"] "wasabi-csi"]
                                      [["persistence" "persistentVolumeClaim" "database" "storageClass"] "wasabi-csi"]
                                      [["persistence" "persistentVolumeClaim" "jobservice" "storageClass"] "wasabi-csi"]
                                      [["persistence" "persistentVolumeClaim" "redis" "storageClass"] "wasabi-csi"]])))}})