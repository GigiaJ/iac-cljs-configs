(ns k8s.add-ons.csi-driver.hetzner 
  (:require 
   [configs :refer [cfg]]))

(def config
  {:stack [:k8s:secret :k8s:chart]
   :app-namespace "kube-system"
   :app-name "hcloud-csi"
   :vault-load-yaml false
   :k8s:secret-opts {:metadata {:name "hcloud"
                            :namespace "kube-system"}
                 :stringData {:token  (-> cfg :hcloudToken)}}
   :k8s:chart-opts {:fetchOpts {:repo "https://charts.hetzner.cloud"}
                :values {:controller {:enabled false
                                      :existingSecret {:name "hcloud-csi-secret"}
                                      :node {:existingSecret {:name "hcloud-csi-secret"}}}}}})