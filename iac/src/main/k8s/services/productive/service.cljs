(ns k8s.services.productive.service)

(def config
  {:stack [:vault-secrets :deployment :service :ingress]
   :app-namespace "generic"
   :app-name      "superproductivity"
   :image-port     80
   :image   "docker.io/johannesjo/super-productivity:latest"})