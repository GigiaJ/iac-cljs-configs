(ns k8s.services.openbao.openbao
 (:require
  ["@pulumi/kubernetes" :as k8s]
  ["fs" :as fs]
  ["js-yaml" :as yaml]
  ["path" :as path]
  [clojure.core.async :refer [go]]))

(defn deploy-vault
  "Deploy OpenBao via Helm chart on the given Kubernetes provider."
  [provider]
 (let [core-v1 (.. k8s -core -v1)
            helm-v3 (.. k8s -helm -v3)

            vault-ns ((.. core-v1 -Namespace)
                      "vault-ns"
                      (clj->js {:metadata {:name "vault"}})
                      (clj->js {:provider provider}))

            values-path (.join path js/__dirname "values.yaml")
            helm-values (-> values-path
                            (fs/readFileSync "utf8")
                            (yaml/load))

            chart ((.. helm-v3 -Chart)
                   "openbao"
                   (clj->js {:chart "openbao"
                             :fetchOpts {:repo "https://openbao.github.io/openbao-helm"}
                             :namespace (.. vault-ns -metadata -name)
                             :values helm-values})
                   (clj->js {:provider provider
                             :dependsOn [vault-ns]}))]
    {:namespace vault-ns
     :chart chart}))
