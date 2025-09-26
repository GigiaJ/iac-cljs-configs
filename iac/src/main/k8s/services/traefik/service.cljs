(ns k8s.services.traefik.service
  (:require
   ["@pulumi/kubernetes" :as k8s]))

(defn set-up-traefik [provider]
  (let [helm-v3 (.. k8s -helm -v3)
        chart (new (.. helm-v3 -Chart) "traefik"
                   (clj->js {:chart "traefik"
                             :fetchOpts {:repo "https://helm.traefik.io/traefik"}
                             :namespace "kube-system"
                             :values {:deployment {:kind "DaemonSet"}
                                      :ports {:web {:hostPort 80}
                                              :websecure {:hostPort 443}}
                                      :service {:type "ClusterIP"}
                                      }})
                   (clj->js {:provider provider}))]
    chart))