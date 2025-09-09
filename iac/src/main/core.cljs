(ns core
  (:require
   ["@pulumi/pulumi" :as pulumi]
   [base :as base]
   [k8s.services.nextcloud.nextcloud :as nextcloud]))


(defn app-list [provider vault-params]
  (let [nextcloud-result (nextcloud/deploy-nextcloud provider vault-params)]
    {:nextcloud nextcloud-result}))


(defn main! []
  (let [init (base.init! app-list)
        cluster (get init :cluster)
        app-outputs (get init :setup)]

    (set! (.-exports js/module)
          (clj->js {:kubeconfig (get cluster :kubeconfig)
                    :masterIp (get cluster :masterIp)
                    :workerDeIp (get cluster :workerDeIp)
                    :workerUsIp (get cluster :workerUsIp)

                    :vaultAddress (.apply app-outputs #(get-in % [:vault :address]))
                    :vaultToken (.apply app-outputs #(get-in % [:vault :root-token]))

                    :nextcloudUrl (.apply app-outputs
                                          (fn [outputs]
                                            (get-in outputs [:nextcloud :nextcloud-url])))}))))