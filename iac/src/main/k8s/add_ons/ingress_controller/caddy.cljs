(ns k8s.add-ons.ingress-controller.caddy (:require [configs :refer [cfg]]))

(def config
  {:stack [:docker-image :vault-secrets :helm-fn :chart]
   :app-namespace "caddy-system"
   :app-name      "caddy-ingress-controller"
   :chart-repo    "https://caddyserver.github.io/ingress"
   :image-port 8080
   :vault-load-yaml false
   :helm-values-fn #(clj->js
                     {:ingressController
                      {:deployment {:kind "DaemonSet"}
                       :daemonSet {:useHostPort true}
                       :ports {:web {:hostPort 80}
                               :websecure {:hostPort 443}}
                       :service {:type "NodePort"
                                 :externalTrafficPolicy "Local"}
                       :image {:repository (-> cfg :docker-repo)
                               :tag "latest"}
                       :config {:email (-> cfg :dns-email)}}})})