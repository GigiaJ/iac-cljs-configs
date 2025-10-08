(ns utils.k8s
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   [utils.vault :as vault-utils]
   ["@pulumi/docker" :as docker] 
   ["path" :as path]
   [configs :refer [cfg]]))

(defn assoc-ins [m path-vals]
  (reduce (fn [acc [path val]] (assoc-in acc path val)) m path-vals))

(defn make-transformer
  "Given f that takes {:app-name .. :secrets ..}, where :secrets is a plain map
   (already unwrapped inside .apply), return a Helm transformer."
  [f]
  (fn [{:keys [base-values app-name secrets]}]
    (.apply secrets
            (fn [smap]
              (let [m (js->clj smap :keywordize-keys true)
                    updates (f {:app-name app-name
                                :secrets  m})
                    after (clj->js (assoc-ins base-values updates))]
                after)))))

(defn create-secret [provider app-namespace app-name secrets-data]
    (new (.. k8s -core -v1  -Secret) (str app-name "-secrets")
         (clj->js {:metadata {:name (str app-name "-secrets")
                              :namespace app-namespace}
                   :stringData secrets-data})
         (clj->js {:provider provider})))

(defn create-image [app-name]
  (let [context-path (.. path (join "." (-> cfg :resource-path)))
        dockerfile-path (.. path (join context-path (str app-name ".dockerfile")))]
  (new (.. docker -Image) app-name
       (clj->js {:build {:context context-path
                         :dockerfile dockerfile-path}
                 :imageName (str (-> cfg :docker-repo) "/" app-name ":latest")}))))

(defn create-ns [provider app-namespace]
  (new (.. k8s -core -v1 -Namespace) app-namespace
       (clj->js {:metadata {:name app-namespace}})
       (clj->js {:provider provider})))

(defn create-chart [provider app-namespace app-name chart-repo helm-fn dependencies transformations ]
  (new (..  k8s -helm -v3 -Chart) app-name
       (clj->js {:chart     app-name
                 :fetchOpts {:repo chart-repo}
                 :namespace app-namespace
                 :values helm-fn
                 :transformations  (if transformations [transformations] [])})
       (clj->js {:provider provider
                 :enableServerSideApply false
                 :dependsOn dependencies})))

(defn create-deployment [provider app-namespace app-name app-labels image image-port dependencies]
  (new (.. k8s -apps -v1 -Deployment) app-name
       (clj->js {:metadata {:namespace app-namespace
                            :name app-name}
                 :spec {:selector {:matchLabels app-labels}
                        :replicas 1
                        :template {:metadata {:labels app-labels}
                                   :spec {:containers
                                          [{:name app-name
                                            :image image
                                            :ports [{:containerPort image-port}]}]}}}})
       (clj->js {:provider provider
                 :dependsOn dependencies})))

(defn create-service [provider app-namespace app-name app-labels image-port dependencies]
  (new (.. k8s -core -v1 -Service) app-name
       (clj->js {:metadata {:namespace app-namespace
                            :name app-name}
                 :spec {:selector app-labels
                        :ports [{:port 80 :targetPort image-port}]}})
       (clj->js {:provider provider :dependsOn dependencies})))

(defn create-ingress [provider app-namespace app-name full-snippet host image-port dependencies]
  (new (.. k8s -networking -v1 -Ingress) app-name
       (clj->js
        {:metadata {:name app-name
                    :namespace app-namespace
                    :annotations {"caddy.ingress.kubernetes.io/snippet" full-snippet}}
         :spec
         {:ingressClassName "caddy"
          :rules [{:host host
                   :http {:paths [{:path "/"
                                   :pathType "Prefix"
                                   :backend {:service {:name app-name
                                                       :port {:number image-port}}}}]}}]}})
       (clj->js {:provider provider :dependsOn dependencies})))

(defn deploy-stack
  "Deploys a versatile stack of K8s resources, including optional Helm charts."
  [& args]
  (let [[component-kws [options]] (split-with keyword? args)
        requested-components (set component-kws)

        {:keys [provider vault-provider pulumi-cfg app-namespace app-name image image-port caddy-snippet vault-load-yaml chart-repo transformations exec-fn helm-values-fn]
         :or {vault-load-yaml false image-port 80 caddy-snippet "" helm-values-fn #(:base-values %) transformations nil}} options
        app-labels {:app app-name}

        full-snippet (str "tls {\n  dns cloudflare {env.CLOUDFLARE_API_TOKEN}\n}\n" caddy-snippet)

        prepared-vault-data (when (requested-components :vault-secrets)
                              (vault-utils/prepare {:provider       provider
                                                    :vault-provider vault-provider
                                                    :app-name       app-name
                                                    :app-namespace  app-namespace
                                                    :load-yaml      vault-load-yaml}))

        {:keys [secrets yaml-values bind-secrets]} (or prepared-vault-data {:secrets nil :yaml-values nil :bind-secrets nil})


        helm-fn (helm-values-fn {:base-values  yaml-values
                                 :secrets      (if (some? prepared-vault-data) secrets nil)
                                 :app-name    app-name})

        host (when secrets (.apply secrets #(aget % "host")))

        ns (when (requested-components :namespace)
             (create-ns provider app-namespace))

        docker-image (when (requested-components :docker-image)
                       (create-image app-name))

        image (if (some? docker-image) (str (-> cfg :docker-repo) "/" app-name ":latest") image)


        chart (when (requested-components :chart)
                (create-chart provider app-namespace app-name chart-repo helm-fn (vec (filter some? [ns docker-image bind-secrets])) transformations))

        deployment (when (requested-components :deployment)
                     (create-deployment provider app-namespace app-name app-labels image image-port [docker-image]))

        service (when (requested-components :service)
                  (create-service provider app-namespace app-name app-labels image-port [deployment]))

        app-dependency (or service chart bind-secrets)

        ingress (when (requested-components :ingress) (create-ingress provider app-namespace app-name full-snippet host image-port [app-dependency]))
        
        execute (when (requested-components :execute)
                  (exec-fn (assoc options :dependencies  (vec (filter some? [chart ns deployment service ingress docker-image])))))]



    {:namespace ns, :vault-secrets prepared-vault-data, :chart chart, :deployment deployment, :service service, :ingress ingress :execute execute}))