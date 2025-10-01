(ns utils.vault
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   [promesa.core :as p]
   ["fs" :as fs]
   ["js-yaml" :as yaml]
   ["path" :as path]
   [configs :refer [cfg]]))

(defn vault-path-exists? [vault path provider]
  )

(defn get-secret-val
  "Extract a specific key from a Vault secret Output/Promise."
  [secret-promise key]
  (.then secret-promise #(aget (.-data %) key)))

(defn initialize-mount [vault-provider vault-path service-name]
  (let [service-secrets (into {} (get (-> cfg :secrets-json) (keyword service-name)))]
    (new (.. vault -generic -Secret)
         (str service-name "-secret")
         (clj->js {:path (str vault-path)
                   :dataJson (js/JSON.stringify (clj->js service-secrets))})
         (clj->js {:provider vault-provider}))))



(defn prepare [vault-provider service-name provider load-yaml]
  (let [apps-v1 (.. k8s -apps -v1) 
        core-v1 (.. k8s -core -v1)
        helm-v3 (.. k8s -helm -v3)
        vault-path (str "secret/" service-name)
        _ (when vault-provider (initialize-mount vault-provider vault-path service-name))
        secrets (when vault-provider (pulumi/output (.getSecret (.-generic vault)
                            (clj->js {:path vault-path})
                            (clj->js {:provider  vault-provider}))))
        secrets-data (when secrets (.apply secrets #(.. % -data)))
        values-path (.join path js/__dirname ".."  (-> cfg :resource-path) (str service-name ".yml"))
        yaml-values (when load-yaml (js->clj (-> values-path
                                 (fs/readFileSync "utf8")
                                 (yaml/load))))
        ns (when provider (.. (new (.. core-v1 -Namespace)
                                   (str service-name "-ns")
                                   (clj->js {:metadata {:name service-name}})
                                   (clj->js {:provider provider})) -metadata -name))
        bind-secrets (when (and vault-provider provider) (new (.. core-v1 -Secret)
                                         (str service-name "-secrets")
                                         (clj->js {:metadata {:name (str service-name "-secrets")
                                                              :namespace service-name}
                                                   :stringData secrets-data})
                                         (clj->js {:provider provider})))]

  




    {:apps-v1 apps-v1
     :core-v1 core-v1
     :helm-v3 helm-v3
     :secrets secrets-data
     :yaml-path values-path 
     :yaml-values yaml-values
     :namespace ns
     :service-name service-name
     :bind-secrets bind-secrets}))