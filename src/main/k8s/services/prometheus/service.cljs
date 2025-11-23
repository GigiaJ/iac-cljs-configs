(ns k8s.services.prometheus.service)

(def config
  {:stack [:vault-secrets :chart]
   :app-namespace "prometheus"
   :app-name      "prometheus"
   :image-port 8080
   :vault-load-yaml true
   :chart-opts {:chart "kube-prometheus-stack"
                :fetchOpts {:repo "https://prometheus-community.github.io/helm-charts"}
                :namespace "monitoring"
                :values {:grafana {:adminPassword 'password
                                   :ingress {:enabled true
                                             :ingressClassName "caddy"
                                             :hosts ['grafana-host]}
                                   :persistence {:enabled true
                                                 :type "pvc"
                                                 :storageClassName "hcloud-volumes"
                                                 :accessModes ["ReadWriteOnce"]
                                                 :size "10Gi"}}
                         :prometheus {:ingress {:enabled true
                                                :ingressClassName "caddy"
                                                :hosts ['prometheus-host]}
                                      :prometheusSpec {:storageSpec {:volumeClaimTemplate {:spec {:accessModes ["ReadWriteOnce"]
                                                                                                  :storageClassName "hcloud-volumes"
                                                                                                  :resources {:requests {:storage "50Gi"}}}}}}}}}})