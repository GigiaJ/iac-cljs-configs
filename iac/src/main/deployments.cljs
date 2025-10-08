(ns deployments
  (:require
   [base :as base]
   [infra.dns :as dns]
   [k8s.add-ons.ingress-controller.caddy :as caddy]
   [k8s.services.nextcloud.service :as nextcloud-service]
   [k8s.services.mesite.service :as mesite-service]
   [k8s.services.productive.service :as productive-service]))

(defn extended-exports [init] 
  (let [;;exports (base.build-exports init)
        app-outputs (get init :setup)]
    #_(assoc exports :nextcloudUrl (.apply app-outputs #(get-in % [:nextcloud :nextcloud-url
                                                                   ])))
        ))

(defn quick-deploy-services []
  (base/quick-deploy [nextcloud-service/config mesite-service/config productive-service/config] extended-exports))

(defn quick-deploy-shared []
  (base/quick-deploy [caddy/config dns/config] extended-exports))
