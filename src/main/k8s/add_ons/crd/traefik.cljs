(ns k8s.add-ons.crd.traefik)

(def config
  {:stack [:k8s:chart]
   :app-name "traefik-crds"
   :k8s:chart-opts {:repositoryOpts {:repo "https://traefik.github.io/charts"}
                    :chart "traefik-crds"
                    :version "v1.12.0"
                    :namespace "traefik" 
                    :values {:deleteOnUninstall true}}})