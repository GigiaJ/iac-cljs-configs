(ns k8s.csi-driver.hetzner
  (:require ["@pulumi/pulumi" :as pulumi]
            ["@pulumi/kubernetes" :as k8s]))

(defn deploy-csi-driver [provider]
  (let [hcloud-config (pulumi/Config. "hcloud")
        hcloud-token  (.requireSecret hcloud-config "token")
        core-v1       (.. k8s -core -v1)
        helm-v3       (.. k8s -helm -v3)

        csi-secret    (core-v1.Secret.
                       "hcloud-csi-secret"
                       (clj->js {:metadata {:name "hcloud"
                                            :namespace "kube-system"}
                                 :stringData {:token hcloud-token}})
                       #js {:provider provider})

        secret-name   (-> csi-secret .-metadata .-name)

        csi-chart     (helm-v3.Chart.
                       "hcloud-csi"
                       (clj->js {:chart "hcloud-csi"
                                 :fetchOpts {:repo "https://charts.hetzner.cloud"}
                                 :namespace "kube-system"
                                 :values {:controller
                                          {:secret {:enabled false}
                                           :existingSecret {:name secret-name}}
                                          :node
                                          {:existingSecret {:name secret-name}}}})
                       (clj->js {:provider provider
                                 :dependsOn [csi-secret]}))]
    csi-chart))
