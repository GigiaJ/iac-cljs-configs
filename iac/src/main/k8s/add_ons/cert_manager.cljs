(ns k8s.add-ons.cert-manager
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/kubernetes/yaml" :as yaml]
   [utils.vault :as utils]))

(defn deploy [provider]
  (let [{:keys [apps-v1 helm-v3 namespace service-name yaml-path]} (utils/prepare nil "cert-manager" provider true)
        chart (new (.. helm-v3 -Chart)
                   service-name
                   (clj->js {:chart     service-name
                             :fetchOpts {:repo "https://charts.jetstack.io"}
                             :namespace namespace
                             :values    {:installCRDs true}})
                   (clj->js {:provider provider}))
webhook-deployment (.. chart (getResource "apps/v1/Deployment"
                                          "cert-manager-webhook"
                                          namespace))
        cert-manager-yaml (new (.. yaml -ConfigFile)
                         "cert-manager"
                         (clj->js {:file yaml-path})
                         (clj->js {:provider provider
                                   :dependsOn [webhook-deployment]}))]
    {:chart chart
     :issuer cert-manager-yaml}))