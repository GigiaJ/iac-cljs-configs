(ns k8s.add-ons.csi-driver.hetzner 
  (:require 
   [configs :refer [cfg]]))

(def config
  {:stack [:secret :chart]
   :app-namespace "kube-system"
   :app-name "hcloud-csi"
   :vault-load-yaml false
   :secret-opts {:metadata {:name "hcloud"
                            :namespace "kube-system"}
                 :stringData {:token  (-> cfg :hcloudToken)}}
   :chart-opts {:fetchOpts {:repo "https://charts.hetzner.cloud"}
                :helm-values-fn #(clj->js {:controller {:enabled false
                                                        :existingSecret {:name "hcloud-csi-secret"}
                                                        :node {:existingSecret {:name "hcloud-csi-secret"}}}})}})