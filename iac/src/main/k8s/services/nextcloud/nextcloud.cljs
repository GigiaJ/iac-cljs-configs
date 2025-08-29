(ns k8s.services.nextcloud.nextcloud
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   ["fs" :as fs]
   ["js-yaml" :as yaml]
   ["path" :as path]
   [clojure.core.async :refer [go]]))

(defn- get-secret-val
  "Extract a specific key from a Vault secret Output/Promise."
  [secret-promise key]
  (.then secret-promise #(aget (.-data %) key)))

(defn deploy-nextcloud
  "Deploy Nextcloud using direct vault connection info."
  [provider vault-params]
  (let [core-v1 (.. k8s -core -v1)
        helm-v3 (.. k8s -helm -v3)

        vault-provider (new vault/Provider
                            "vault-provider"
                            (clj->js vault-params))

        nextcloud-secrets (.getSecret (.-generic vault)
                                      (clj->js {:path "secret/nextcloud"})
                                      (clj->js {:provider  vault-provider
                                                :dependsOn [(get vault-params :vault-port-forward)]}))

        ns (new (.. core-v1 -Namespace)
                "nextcloud-ns"
                (clj->js {:metadata {:name "nextcloud"}})
                (clj->js {:provider provider}))

        admin-secret (new (.. core-v1 -Secret)
                          "nextcloud-admin-secret"
                          (clj->js {:metadata {:name      "nextcloud-admin-secret"
                                               :namespace (.. ns -metadata -name)}
                                    :stringData {:password (get-secret-val nextcloud-secrets "adminPassword")}})
                          (clj->js {:provider provider}))

        db-secret (new (.. core-v1 -Secret)
                       "nextcloud-db-secret"
                       (clj->js {:metadata {:name      "nextcloud-db-secret"
                                            :namespace (.. ns -metadata -name)}
                                 :stringData {"mariadb-root-password" (get-secret-val nextcloud-secrets "dbPassword")
                                              "mariadb-password"      (get-secret-val nextcloud-secrets "dbPassword")}})
                       (clj->js {:provider provider}))

        values-path (.join path js/__dirname "resources" "nextcloud.yml")
        helm-values (-> values-path
                        (fs/readFileSync "utf8")
                        (yaml/load))
        _ (aset (aget (aget (aget helm-values "ingress") "hosts") 0)
                "host"
                (get-secret-val nextcloud-secrets "host"))

        chart (new (.. helm-v3 -Chart)
                   "my-nextcloud"
                   (clj->js {:chart     "nextcloud"
                             :fetchOpts {:repo "https://nextcloud.github.io/helm/"}
                             :namespace (.. ns -metadata -name)
                             :values    helm-values})
                   (clj->js {:provider provider
                             :dependsOn [admin-secret db-secret (clj->js (get vault-params :vault-port-forward))]}))]

    {:namespace    ns
     :admin-secret admin-secret
     :db-secret    db-secret
     :chart        chart
     :nextcloud-url (.then nextcloud-secrets
                           #(str "https://" (aget (.-data %) "host")))}))
