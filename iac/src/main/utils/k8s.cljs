(ns utils.k8s (:require ["@pulumi/kubernetes" :as k8s]))





(defn ingress [{:keys [app-name app-namespace host ingress-opts]}]
  {:metadata {:name app-name
              :namespace app-namespace
              :annotations {"caddy.ingress.kubernetes.io/tls.issuer" "cloudflare"
                            "caddy.ingress.kubernetes.io/tls.dns_provider" "cloudflare"
                            "caddy.ingress.kubernetes.io/tls.dns_provider.credentials_secret" "caddy-ingress-controller-secrets"
                            "caddy.ingress.kubernetes.io/tls.dns_provider.credentials_secret_namespace" "caddy-system"
                            "caddy.ingress.kubernetes.io/tls.issuer.acme_ca" "https://acme-v02.api.letsencrypt.org/directory"
                            "caddy.ingress.kubernetes.io/snippet" (:caddy-snippet ingress-opts)}}
   :spec {:ingressClassName "caddy"
          :rules [{:host host
                   :http {:paths [{:path "/"
                                   :pathType "Prefix"
                                   :backend {:service {:name app-name
                                                       :port {:number 80}}}}]}}]}})

(defn chart [{:keys [app-name app-namespace]}]
  {:chart     app-name
   :namespace app-namespace
   :transformations []})

(defn config-map [{:keys  [app-name app-namespace]}]
  {:metadata {:namespace app-namespace
              :name app-name}
   :data {}})

(defn service [{:keys  [app-name app-namespace app-labels image-port]}]
  {:metadata {:namespace app-namespace
              :name app-name}
   :spec {:selector app-labels
          :ports [{:port 80 :targetPort image-port}]}})

(defn deployment [{:keys [app-name app-namespace app-labels image image-port]}]
  {:metadata {:namespace app-namespace
              :name app-name}
   :spec {:selector {:matchLabels app-labels}
          :replicas 1
          :template {:metadata {:labels app-labels}
                     :spec {:containers
                            [{:name app-name
                              :image image
                              :ports [{:containerPort image-port}]}]}}}})


(defn nspace [{:keys [app-namespace]}]
  {:metadata {:name app-namespace}})

(defn secret [{:keys [app-name app-namespace]}]
  {:metadata {:name (str app-name "-secrets")
              :namespace app-namespace}})

(defn storage-class [{:keys [app-name]}]
  {:metadata {:name app-name}})

(def defaults
  {:ingress       ingress
   :chart         chart
   :config-map    config-map
   :service       service
   :deployment    deployment
   :namespace     nspace
   :secret        secret
   :storage-class storage-class})

(def component-specs-defs
  {:root-sym 'k8s
   :provider-key :k8s
   :resources
   {:namespace  {:path ['-core '-v1 '-Namespace]}
    :secret     {:path ['-core '-v1 '-Secret]}
    :deployment {:path ['-apps '-v1 '-Deployment]}
    :service    {:path ['-core '-v1 '-Service]}
    :ingress    {:path ['-networking '-v1 '-Ingress]}
    :chart      {:path ['-helm '-v3 '-Chart]
                 :defaults-fn
                 '(fn [env]
                    (deep-merge (default/chart (:options env))
                                (update-in (get-in (:options env) [:k8s:chart-opts]) [:values]
                                           #(deep-merge % (or (:yaml-values (:options env)) {})))))}}})

#_(def component-specs
  :k8s:namespace {:constructor (.. k8s -core -v1 -Namespace)
                  :provider-key :k8s
                  :defaults-fn (fn [env] (defaults/namespace (:options env)))}
  
  :k8s:secret {:constructor (.. k8s -core -v1 -Secret)
               :provider-key :k8s
               :defaults-fn (fn [env] (default/secret (:options env)))}
  
  :k8s:deployment {:constructor (.. k8s -apps -v1 -Deployment)
                   :provider-key :k8s
                   :defaults-fn (fn [env] (default/deployment (:options env)))}
  
  :k8s:service {:constructor (.. k8s -core -v1 -Service)
                :provider-key :k8s
                :defaults-fn (fn [env] (default/service (:options env)))}
  
  :k8s:ingress {:constructor (.. k8s -networking -v1 -Ingress)
                :provider-key :k8s
                :defaults-fn (fn [env] (default/ingress (:options env)))}
  
  :k8s:chart {:constructor (.. k8s -helm -v3 -Chart)
              :provider-key :k8s
              :defaults-fn (fn [env]
                             (deep-merge (default/chart (:options env))
                                         (update-in (get-in (:options env) [:k8s:chart-opts]) [:values]
                                                    #(deep-merge % (or (:yaml-values (:options env)) {})))))})

(def provider-template
 {:constructor (.. k8s -Provider)
  :name "k8s-provider"
  :config {:kubeconfig 'kubeconfig}})


(defn pre-deploy-rule
  "k8s pre-deploy rule: scans the service registry and creates
   all unique namespaces. Returns a map of created namespaces
   keyed by their name."
  [{:keys [service-registry provider]}]
  (let [namespaces (->> service-registry
                        (remove #(contains? % :no-namespace))
                        (map :app-namespace)
                        (remove nil?)
                        (set))]
    (into {}
          (for [ns-name namespaces]
            (let [resource-name ns-name
                  ns-config {:metadata {:name resource-name
                                        :namespace ns-name}}
                  ns-resource (new (.. k8s -core -v1 -Namespace) resource-name
                                   (clj->js ns-config)
                                   (clj->js {:provider provider}))]
              [ns-name ns-resource])))))