(ns k8s.add-ons.gateway.traefik)

(def config
  {:stack [:vault:prepare [:k8s :secret :chart :gateway-class :gateway :certificates]]
   :app-namespace "traefik"
   :app-name "traefik"
   :is-prod? true
   :vault-load-yaml false
   :k8s:chart-opts {:skipCrds true
                    :repositoryOpts {:repo 'repo}
                    :chart 'chart
                    :transformations  [(fn [args _opts] (let [kind (get-in args [:resource :kind])]
                                       (if (= kind "CustomResourceDefinition")
                                         nil
                                         args)))]
                    :version "37.3.0"
                    :namespace "traefik"
                    :values {:providers {:kubernetesGateway {:enabled true}}
                             :gatewayClass {:enabled false}
                             :gateway {:enabled false}
                             :ports {:web {:port 8000
                                           :expose {:default true}
                                           :exposedPort 80
                                           :protocol "TCP"}

                                     :websecure {:port 8443
                                                 :expose {:default true}
                                                 :exposedPort 443
                                                 :protocol "TCP"
                                                 :transport {:respondingTimeouts
                                                             {:readTimeout "600s"
                                                              :writeTimeout "600s"
                                                              :idleTimeout "600s"}}}}}}
   :k8s:gateway-opts
   {:metadata {:name "main-gateway"
               :namespace "traefik"}
    :spec {:gatewayClassName "traefik"
           :listeners '(make-listeners domains)}}
   
   :k8s:gateway-class-opts
   {:spec {:controllerName "traefik.io/gateway-controller"}}
   })
