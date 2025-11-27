(ns k8s.add-ons.crd.gateway-api)

(def config
  {:stack [:k8s:config-file]
   :app-name "gateway-api"
   :version "v1.4.0"
   :k8s:config-file-opts {:file '(str "https://github.com/kubernetes-sigs/gateway-api/releases/download/" version "/experimental-install.yaml")}})