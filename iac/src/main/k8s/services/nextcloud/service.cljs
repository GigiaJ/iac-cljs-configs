(ns k8s.services.nextcloud.service
  (:require
   ["@pulumi/pulumi" :as pulumi]
   [utils.vault :as vault-utils]
   [utils.ingress :as ingress-utils]))

(defn- add-skip-await-transformation
  "A Pulumi transformation that adds the skipAwait annotation to problematic resources."
  [args _opts]
  (let [kind (get-in args [:kind])]
    (if (or
         (= kind "StatefulSet")
         (= kind "PersistentVolumeClaim")
         (= kind "Ingress"))
      (let [metadata (get-in args [:metadata] {})
            annotations (get metadata :annotations {})
            new-annotations (assoc annotations "pulumi.com/skipAwait" "true")
            new-metadata (assoc metadata :annotations new-annotations)]
        (assoc args :metadata new-metadata))
      args)))

(defn deploy-nextcloud
  "Deploy Nextcloud using direct vault connection info."
  [provider vault-provider]
  (let [{:keys [helm-v3 secrets yaml-values service-name namespace bind-secrets]} (vault-utils/prepare vault-provider "nextcloud" provider)

        hostname (.. secrets -host)

        final-helm-values (-> yaml-values
                              (assoc-in [:ingress :enabled] false)
                              (assoc-in [:nextcloud :host] hostname)
                              (assoc-in [:nextcloud :trusted_domains] [hostname]))
        chart (new (.. helm-v3 -Chart)
                   service-name
                   (clj->js {:chart     service-name
                             :fetchOpts {:repo "https://nextcloud.github.io/helm/"}
                             :namespace namespace
                             :values    final-helm-values})
                   (clj->js {:provider provider
                             :dependsOn [bind-secrets]
                             :transformations [add-skip-await-transformation]
                             }))
        ingress (ingress-utils/create-ingress hostname namespace service-name 80 chart)
        ;;cert (ingress-utils/create-certificate hostname namespace service-name ingress)
        ]
    {:namespace    namespace
     :nextcloud-secrets bind-secrets
     :chart        chart
     :ingress ingress
     :nextcloud-url (str "https://" hostname)}))
