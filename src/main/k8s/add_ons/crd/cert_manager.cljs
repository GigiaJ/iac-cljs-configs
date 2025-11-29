(ns k8s.add-ons.crd.cert-manager)

(def config
  {:stack [:k8s:config-file]
   :app-name "cert-manager"
   :version "v1.19.1"
   :k8s:config-file-opts {:file '(str "https://github.com/cert-manager/cert-manager/releases/download/" version "/cert-manager.crds.yaml")}})