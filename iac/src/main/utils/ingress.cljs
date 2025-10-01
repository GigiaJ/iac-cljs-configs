(ns utils.ingress
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/kubernetes/apiextensions" :as cr]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   [promesa.core :as p]
   ["fs" :as fs]
   ["js-yaml" :as yaml]
   ["path" :as path]
   [configs :refer [cfg]]))

(defn create-ingress [hostname namespace service-name port dependency]
  (new (.. k8s -networking -v1 -Ingress)
       (str service-name "-ingress")
       (clj->js
        {:metadata {:name service-name
                    :namespace namespace
                    :annotations {"pulumi.com/skipAwait" "true"
                                  "caddy.ingress.kubernetes.io/snippet"
                                  (str "tls {\n"
                                       "  dns cloudflare {env.CLOUDFLARE_API_TOKEN}\n"
                                       "}")}}
         :spec
         {:ingressClassName "caddy"

          :rules
          [{:host hostname
            :http {:paths [{:path "/"
                            :pathType "Prefix"
                            :backend {:service {:name service-name
                                                :port {:number port}}}}]}}]}})
       (clj->js
        {:dependsOn [dependency]
         :skipAwait true})))

(defn create-certificate [hostname namespace service-name dependency]
  (new (.. cr -CustomResource)
       (str service-name "-certificate")
       (clj->js
        {:apiVersion "cert-manager.io/v1"
         :kind       "Certificate"
         :metadata   {:name      (str service-name "-certificate")
                      :namespace namespace}
         :spec       {:secretName (str service-name "-tls-secret")
                      :dnsNames   [hostname]
                      :issuerRef  {:name "letsencrypt-staging"
                                   :kind "ClusterIssuer"}}})
       (clj->js
        {:dependsOn [dependency]})))