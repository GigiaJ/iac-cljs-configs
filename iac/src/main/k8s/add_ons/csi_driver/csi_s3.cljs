(ns k8s.add-ons.csi-driver.csi-s3)

(def config
  {:stack [:namespace :vault-secrets :chart :ingress]
   :app-namespace "csi-s3"
   :app-name      "kube-system"
   :chart-repo    "https://yandex-cloud.github.io/k8s-csi-s3"})