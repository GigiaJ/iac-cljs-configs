(ns deployments
  (:require
   ["@pulumi/pulumi" :as pulumi]
   [base :as base]
   [k8s.services.nextcloud.nextcloud :as nextcloud]))


(defn app-list [provider vault-params]
  (let [nextcloud-result (nextcloud/deploy-nextcloud provider vault-params)]
    {:nextcloud nextcloud-result}))

(defn extended-exports [app-outputs exports]
  (assoc exports :nextcloudUrl (.apply app-outputs #(get-in % [:nextcloud :nextcloud-url]))))

(defn deploy-services []
  (let [init (base/initialize app-list)]
    (set! (.-exports js/module)
          (clj->js (extended-exports (get init :setup) (base.build-exports init))))))
