(ns k8s.services.productive.service)

(def config
  {:stack [:vault:prepare [:k8s :deployment :service :httproute]]
   :app-namespace "generic"
   :app-name      "superproductivity"
   :image-port     80
   :image   "docker.io/johannesjo/super-productivity:latest"
   :k8s:httproute-opts {:spec {::hostnames ['host]}}
   })