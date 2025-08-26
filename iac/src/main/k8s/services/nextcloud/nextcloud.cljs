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

(defn deploy-nextcloud-app
  "Deploy Nextcloud using Vault‑managed secrets and a Helm chart."
  [provider]
  (let [core-v1 (.. k8s -core -v1)
            helm-v3 (.. k8s -helm -v3)

            vault-cfg (pulumi/Config. "vault")
            vault-provider (vault/Provider.
                            "vault-provider"
                            (clj->js {:address (.require vault-cfg "address")
                                      :token   (.requireSecret vault-cfg "token")}))

            nextcloud-secrets (.getSecret (.-generic vault)
                               (clj->js {:path "secret/nextcloud"})
                               (clj->js {:provider vault-provider}))

            ns ((.. core-v1 -Namespace)
                "nextcloud-ns"
                (clj->js {:metadata {:name "nextcloud"}})
                (clj->js {:provider provider}))

            admin-secret ((.. core-v1 -Secret)
                          "nextcloud-admin-secret"
                          (clj->js {:metadata {:name      "nextcloud-admin-secret"
                                               :namespace (.. ns -metadata -name)}
                                    :stringData {:password (get-secret-val nextcloud-secrets "adminPassword")}})
                          (clj->js {:provider provider}))

            db-secret ((.. core-v1 -Secret)
                       "nextcloud-db-secret"
                       (clj->js {:metadata {:name      "nextcloud-db-secret"
                                            :namespace (.. ns -metadata -name)}
                                 :stringData {"mariadb-root-password" (get-secret-val nextcloud-secrets "dbPassword")
                                              "mariadb-password"      (get-secret-val nextcloud-secrets "dbPassword")}})
                       (clj->js {:provider provider}))

            values-path (.join path js/__dirname "values.yaml")
            helm-values (-> values-path
                            (fs/readFileSync "utf8")
                            (yaml/load))
            _ (aset (aget (aget (aget helm-values "ingress") "hosts") 0)
                    "host"
                    (get-secret-val nextcloud-secrets "host"))

            chart ((.. helm-v3 -Chart)
                   "my-nextcloud"
                   (clj->js {:chart     "nextcloud"
                             :fetchOpts {:repo "https://nextcloud.github.io/helm/"}
                             :namespace (.. ns -metadata -name)
                             :values    helm-values})
                   (clj->js {:provider provider
                             :dependsOn [admin-secret db-secret]}))]

        {:namespace    ns
         :admin-secret admin-secret
         :db-secret    db-secret
         :chart        chart
         :nextcloud-url (.then nextcloud-secrets
                               #(str "https://" (aget (.-data %) "host")))}))
