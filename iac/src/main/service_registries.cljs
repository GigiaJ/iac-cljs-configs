(ns service-registries
  (:require    [infra.openbao :as openbao]
               [k8s.add-ons.csi-driver.hetzner :as hetzner-csi]
               [infra.dns :as dns]
               [k8s.add-ons.ingress-controller.caddy :as caddy]
               [k8s.add-ons.csi-driver.wasabi :as wasabi-csi]
               [k8s.add-ons.image-registry.harbor :as harbor]
               [k8s.services.nextcloud.service :as nextcloud-service]
               [k8s.services.mesite.service :as mesite-service]
               [k8s.services.productive.service :as productive-service]))


(def base-service-registry [hetzner-csi/config wasabi-csi/config openbao/config])

(def shared-service-registry [caddy/config dns/config harbor/config])

(def deployment-service-registry [nextcloud-service/config mesite-service/config productive-service/config])