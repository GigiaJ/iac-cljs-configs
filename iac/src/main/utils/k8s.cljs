(ns utils.k8s
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   [utils.vault :as vault-utils]
   [utils.general :refer [generic-transform deep-merge new-resource]]
   ["@pulumi/docker" :as docker]
   ["path" :as path]
   [configs :refer [cfg]]))


(defn default-ingress [{:keys [app-name app-namespace host image-port ingress-opts]}]
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
                                                       :port {:number image-port}}}}]}}]}})

(defn default-chart [{:keys [app-name app-namespace]}]
  {:chart     app-name
   :namespace app-namespace
   :transformations []})

(defn default-config-map [{:keys  [app-name app-namespace]}]
  {:metadata {:namespace app-namespace
              :name app-name}
   :data {}})

(defn default-service [{:keys  [app-name app-namespace app-labels image-port]}]
  {:metadata {:namespace app-namespace
              :name app-name}
   :spec {:selector app-labels
          :ports [{:port 80 :targetPort image-port}]}})

(defn default-deployment [{:keys [app-name app-namespace app-labels image image-port]}]
  {:metadata {:namespace app-namespace
              :name app-name}
   :spec {:selector {:matchLabels app-labels}
          :replicas 1
          :template {:metadata {:labels app-labels}
                     :spec {:containers
                            [{:name app-name
                              :image image
                              :ports [{:containerPort image-port}]}]}}}})

(defn default-image [{:keys [app-name]}]
  (let [context-path (.. path (join "." (-> cfg :resource-path)))
        dockerfile-path (.. path (join context-path (str app-name ".dockerfile")))
        base-args {:build {:context context-path
                           :dockerfile dockerfile-path}
                   :imageName (str (-> cfg :docker-repo) "/" app-name ":latest")}]
    base-args))


(defn default-namespace [{:keys [app-namespace]}]
  {:metadata {:name app-namespace}})

(defn default-secret [{:keys [app-name app-namespace]}]
  {:metadata {:name (str app-name "-secrets")
              :namespace app-namespace}})

(defn default-storage-class [{:keys [app-name]}]
  {:metadata {:name app-name}})

(defn create-resource [resource-type provider app-name dependencies opts]
  (let [resource-class (case resource-type
                         :docker-image         (.. docker -Image)
                         :ingress       (.. k8s -networking -v1 -Ingress)
                         :secret        (.. k8s -core -v1 -Secret)
                         :namespace     (.. k8s -core -v1 -Namespace)
                         :deployment    (.. k8s -apps -v1 -Deployment)
                         :service       (.. k8s -core -v1 -Service)
                         :chart         (.. k8s -helm -v3 -Chart)
                         :config-map    (.. k8s -core -v1 -ConfigMap)
                         :storage-class (.. k8s -storage -v1 -StorageClass)
                         (throw (js/Error. (str "Unknown resource type: " resource-type))))]
    (new-resource resource-class app-name opts provider dependencies)))

(defn create-component
  "Checks if a component is requested and, if so, creates it using generic-transform."
  [requested-components
   resource-type
   provider
   app-name
   dependencies
   component-opts
   defaults
   secrets
   options]

  (when (requested-components resource-type)
    (generic-transform
     (fn [final-args]
       (create-resource resource-type provider app-name dependencies final-args))
     component-opts
     defaults
     secrets
     options)))

(defn deploy-stack
  "Deploys a versatile stack of K8s resources, including optional Helm charts."
  [& args]
  (let [[component-kws [options]] (split-with keyword? args)
        requested-components (set component-kws)

        {:keys [provider vault-provider pulumi-cfg app-namespace app-name image image-port vault-load-yaml exec-fn
                storage-class-opts secret-opts config-map-opts ns-opts image-opts ingress-opts service-opts deployment-opts chart-opts]
         :or {vault-load-yaml false image-port 80}} options
        options (merge options {:app-labels {:app app-name} :image-port image-port})

        prepared-vault-data (when (requested-components :vault-secrets)
                              (vault-utils/prepare {:provider       provider
                                                    :vault-provider vault-provider
                                                    :app-name       app-name
                                                    :app-namespace  app-namespace
                                                    :load-yaml      vault-load-yaml}))

        {:keys [secrets yaml-values bind-secrets]} (or prepared-vault-data {:secrets nil :yaml-values nil :bind-secrets nil})
        host (when secrets (.apply secrets #(aget % "host")))

        ns (create-component requested-components :namespace provider app-namespace nil ns-opts (default-namespace options) secrets options)
        docker-image (create-component requested-components :docker-image nil app-name nil image-opts (default-image options) secrets options)
        secret (create-component requested-components :secret provider app-name nil secret-opts (default-secret options) secrets options)
        config-map (create-component requested-components :config-map provider app-name nil config-map-opts (default-config-map options) secrets options)
        storage-class (create-component requested-components :storage-class provider app-name nil storage-class-opts (default-storage-class options) secrets options)
        deployment (create-component requested-components :deployment provider app-name (vec (filter some? [ns docker-image bind-secrets])) deployment-opts (default-deployment options) secrets options)
        service (create-component requested-components :service provider app-name (vec (filter some?  [ns deployment bind-secrets])) service-opts (default-service options) secrets options)
        chart (create-component requested-components :chart provider app-name
                                (vec (filter some? [ns docker-image bind-secrets]))
                                chart-opts
                                (deep-merge (default-chart options)
                                            (update-in chart-opts [:values] #(deep-merge % (or yaml-values {}))))
                                secrets
                                options)
        ingress (create-component requested-components :ingress provider app-name (vec (filter some? [service chart bind-secrets])) ingress-opts (default-ingress (assoc options :host host)) secrets options)
        execute (when (requested-components :execute)
                  (exec-fn (assoc options :dependencies  (vec (filter some? [chart ns secret storage-class deployment service ingress docker-image])))))]



    {:namespace ns, :vault-secrets prepared-vault-data, :secret secret, :config-map config-map, :docker-image docker-image :storage-class storage-class, :chart chart, :deployment deployment, :service service, :ingress ingress :execute execute}))