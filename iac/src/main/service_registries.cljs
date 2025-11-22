(ns service-registries
  (:require
   [infra.init :as init]
   [infra.openbao :as openbao]
   [k8s.add-ons.csi-driver.hetzner :as hetzner-csi]
   [infra.dns :as dns]
   [infra.buildkit :as buildkit]
   [k8s.preparers.harbor :as harbor-prepare]
   
   [k8s.add-ons.ingress-controller.caddy :as caddy]
   [k8s.add-ons.gateway.traefik :as traefik]
   [k8s.add-ons.cert-manager :as cert-manager]
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

(defn general-provider-output-refs []
  {:vault  {:stack :init
            :outputs ["vaultAddress" "vaultToken"]}
   :harbor {:stack :shared
            :outputs ["username" "password" "url"]}
   :k8s   {:stack :base
           :outputs ["kubeconfig"]}})

(defn create-resource-definition [resource-configs stack-references provider-external-inputs]
  {:resource-configs resource-configs
   :stack-references stack-references
   :provider-external-inputs provider-external-inputs})

(def base-resources-definition
  (create-resource-definition
   [init/config]
   nil
   nil))

(def initialize-resources-definition
  (create-resource-definition
   [hetzner-csi/config openbao/config]
   ["base"]
   {:k8s   {:stack :base
            :outputs ["kubeconfig"]}}
   ))

(def shared-resources-definition
  (create-resource-definition
   [dns/config wasabi-csi/config proxy/config secret-replicator/config 
    traefik/config cert-manager/config
    harbor/config
    ]
   ["base" "init"]
   (general-provider-output-refs)))

(def preparation-resources-definition
  (create-resource-definition
   [harbor-prepare/config]
   ["base" "init" "shared"]
   (general-provider-output-refs)))


(def deployment-resources-definition
  (create-resource-definition
   [#_buildkit/config #_nextcloud-service/config #_foundryvtt-service/config mesite-service/config #_productive-service/config #_gitea-service/config #_act-runner-service/config]
   ["base" "init" "shared"]
   (general-provider-output-refs)))


(def deployment-matrix-service-registry [])
