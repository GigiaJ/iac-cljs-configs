(ns k8s.add-ons.gateway.traefik)

(def config
  {:stack [:vault:prepare [:k8s :secret :chart :gateway :certificates]]
   :app-namespace "traefik"
   :app-name "traefik"
   :is-prod? true
   :vault-load-yaml false
   :k8s:chart-opts {:fetchOpts {:repo 'repo}
                    :chart 'chart
                    :version "37.3.0"
                    :namespace "traefik"
                    :values {:providers {:kubernetesGateway {:enabled true}}
                             :gatewayClass {:enabled true
                                            :name "traefik"}}}
   :k8s:gateway-opts
   {:metadata {:name "main-gateway"
               :namespace "traefik"}
    :spec {:gatewayClassName "traefik"
           :listeners '(make-listeners domains)}}})
