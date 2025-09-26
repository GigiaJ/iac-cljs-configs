(ns k8s.services.nextcloud.service
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   ["fs" :as fs]
   ["js-yaml" :as yaml]
   ["path" :as path]))

(defn- add-skip-await-transformation
  "A Pulumi transformation that adds the skipAwait annotation to problematic resources."
  [args _opts]
  (let [kind (get-in args [:kind])]
    (if (or (= kind "StatefulSet")
            (= kind "PersistentVolumeClaim")
            (= kind "Ingress"))
      (let [
            metadata (get-in args [:metadata] {})
            annotations (get metadata :annotations {})
            new-annotations (assoc annotations "pulumi.com/skipAwait" "true")
            new-metadata (assoc metadata :annotations new-annotations)]
        (assoc args :metadata new-metadata))
      args)))

(defn- get-secret-val
  "Extract a specific key from a Vault secret Output/Promise."
  [secret-promise key]
  (.then secret-promise #(aget (.-data %) key)))

(defn deploy-nextcloud
  "Deploy Nextcloud using direct vault connection info."
  [provider vault-provider]
  (let [core-v1 (.. k8s -core -v1)
        helm-v3 (.. k8s -helm -v3)
        nextcloud-secrets (.getSecret (.-generic vault)
                                      (clj->js {:path "secret/nextcloud"})
                                      (clj->js {:provider  vault-provider}))

        ns (new (.. core-v1 -Namespace)
                "nextcloud-ns"
                (clj->js {:metadata {:name "my-nextcloud"}})
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

        values-path (.join path js/__dirname ".." "resources" "nextcloud.yml")
        helm-values (js->clj (-> values-path
                        (fs/readFileSync "utf8")
                        (yaml/load)))
        hostname (get-secret-val nextcloud-secrets "host")
        
        final-helm-values (-> helm-values
                              (assoc-in [:ingress :hosts 0 :host] hostname)
                               (assoc-in [:ingress :enabled] true)
                              (assoc-in [:nextcloud :host] hostname)
                              (assoc-in [:nextcloud :trusted_domains] [hostname])) 
        
        
        chart (new (.. helm-v3 -Chart)
                   "nextcloud"
                   (clj->js {:chart     "nextcloud"
                             :fetchOpts {:repo "https://nextcloud.github.io/helm/"}
                             :namespace (.. ns -metadata -name)
                             :values    final-helm-values})
                   (clj->js {:provider provider
                             :dependsOn [admin-secret db-secret]
                             :transformations [add-skip-await-transformation]}))]

    {:namespace    ns
     :admin-secret admin-secret
     :db-secret    db-secret
     :chart        chart
     :nextcloud-url (.then nextcloud-secrets
                           #(str "https://" (aget (.-data %) "host")))}))
