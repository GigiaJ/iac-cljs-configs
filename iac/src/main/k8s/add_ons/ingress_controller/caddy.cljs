(ns k8s.add-ons.ingress-controller.caddy (:require [configs :refer [cfg]]))

(def config
  {:stack [:docker-image :vault-secrets :chart]
   :app-namespace "caddy-system"
   :app-name      "caddy-ingress-controller"
   :image-port 8080
   :vault-load-yaml false
   :image-opts {:imageName '(str repo "/" app-name ":latest")}
   :chart-opts {:fetchOpts {:repo "https://caddyserver.github.io/ingress"}
                :values
                {:ingressController
                 {:deployment {:kind "DaemonSet"}
                  :daemonSet {:useHostPort true}
                  :ports {:web {:hostPort 80}
                          :websecure {:hostPort 443}}
                  :service {:type "NodePort"
                            :externalTrafficPolicy "Local"}
                  :image {:repository 'repo
                          :tag "latest"}
                  :config {:email 'email}}}}})