(ns service-registries
  (:require
   [infra.init :as init]
   [infra.openbao :as openbao]
   [k8s.add-ons.csi-driver.hetzner :as hetzner-csi]
   [infra.dns :as dns]
   [infra.buildkit :as buildkit]
   [k8s.preparers.harbor :as harbor-prepare]
   [k8s.add-ons.ingress-controller.caddy :as caddy]
   [k8s.add-ons.csi-driver.wasabi :as wasabi-csi]
   [k8s.add-ons.image-registry.harbor :as harbor]
   [k8s.add-ons.secret-replicator :as secret-replicator]
   [k8s.add-ons.minio :as minio]
   [k8s.add-ons.s3proxy :as s3proxy]
   [k8s.add-ons.proxy :as proxy]
   [k8s.services.nextcloud.service :as nextcloud-service]
   [k8s.services.mesite.service :as mesite-service]
   [k8s.services.gitea.service :as gitea-service]
   [k8s.services.act-runner.service :as act-runner-service]
   [k8s.services.foundryvtt.service :as foundryvtt-service]
   [k8s.services.productive.service :as productive-service]))

(def base-service-registry [init/config hetzner-csi/config openbao/config ])

(def shared-service-registry [#_minio/config dns/config #_caddy/config 
                              #_wasabi-csi/config #_proxy/config #_s3proxy/config #_harbor/config
                              #_secret-replicator/config])

;; Need to move buildkit to base later
(def prepare-service-registry [harbor-prepare/config])

(def deployment-matrix-service-registry [])

(def deployment-service-registry [#_buildkit/config #_nextcloud-service/config #_foundryvtt-service/config mesite-service/config #_productive-service/config #_gitea-service/config #_act-runner-service/config])