(ns k8s.add-ons.csi-driver.hetzner 
  (:require 
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]))

(defn execute-fn [{:keys [provider]}]
(let [hcloud-config (pulumi/Config. "hcloud")
  hcloud-token  (.requireSecret hcloud-config "token")
  csi-secret    (new (.. k8s -core -v1  -Secret)
                 "hcloud-csi-secret"
                 (clj->js {:metadata {:name "hcloud"
                                      :namespace "kube-system"}
                           :stringData {:token hcloud-token}})
                 #js {:provider provider})]
  csi-secret))

(def config
  {:stack [:execute :chart]
   :app-namespace "kube-system"
   :app-name "hcloud-csi"
   :helm-values-fn #(clj->js {:controller {:enabled false
                                 :existingSecret {:name "hcloud-csi-secret"}
                                 :node {:existingSecret {:name "hcloud-csi-secret"}}}})
   :vault-load-yaml false
   :exec-fn execute-fn
   :chart-repo  "https://charts.hetzner.cloud"})