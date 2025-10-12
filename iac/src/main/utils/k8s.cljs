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

(declare deep-merge)

(defn merge-by-name
  "Merges two vectors of maps by :name key."
  [a b]
  (let [a-map (into {} (map #(vector (:name %) %) a))
        b-map (into {} (map #(vector (:name %) %) b))
        merged (merge-with deep-merge a-map b-map)]
    (vec (vals merged))))

(defn deep-merge
  "Recursively merges maps and intelligently merges vectors of maps by :name."
  [a b]
  (cond
    (nil? b) a
    (and (map? a) (map? b))
    (merge-with deep-merge a b)

    (and (vector? a) (vector? b)
         (every? map? a) (every? map? b)
         (some #(contains? % :name) (concat a b)))
    (merge-by-name a b)
    :else b))


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

(defn new-resource [resource-type resource-name final-args provider dependencies]
  (new resource-type resource-name
       (clj->js final-args)
       (clj->js {:provider            provider
                 :enableServerSideApply false
                 :dependsOn           dependencies})))


(defn create-secret [provider app-namespace app-name dependencies secret-options]
  (let [base-args {:metadata {:name (str app-name "-secrets")
                              :namespace app-namespace}}
        final-args (deep-merge base-args secret-options)]
    (new-resource (.. k8s -core -v1  -Secret) app-name final-args provider dependencies)))

(defn create-image [app-name image-options]
  (let [context-path (.. path (join "." (-> cfg :resource-path)))
        dockerfile-path (.. path (join context-path (str app-name ".dockerfile")))
        base-args {:build {:context context-path
                           :dockerfile dockerfile-path}
                   :imageName (str (-> cfg :docker-repo) "/" app-name ":latest")}
        final-args (deep-merge base-args image-options)]
    (new (.. docker -Image) app-name (clj->js final-args))))

(defn create-namespace [provider app-namespace dependencies ns-options]
  (let [base-args {:metadata {:name app-namespace}}
        final-args (deep-merge base-args ns-options)]
    (new-resource (.. k8s -core -v1 -Namespace) app-namespace final-args provider dependencies)))

(defn create-deployment [provider app-namespace app-name app-labels image-name image-port dependencies deployment-options]
  (let [base-args {:metadata {:namespace app-namespace
                              :name app-name}
                   :spec {:selector {:matchLabels app-labels}
                          :replicas 1
                          :template {:metadata {:labels app-labels}
                                     :spec {:containers
                                            [{:name app-name
                                              :image image-name
                                              :ports [{:containerPort image-port}]}]}}}}
        final-args (deep-merge base-args deployment-options)]
    (new-resource (.. k8s -apps -v1 -Deployment) app-name final-args provider dependencies)))

(defn create-service [provider app-namespace app-name app-labels image-port dependencies service-options]
  (let [base-args {:metadata {:namespace app-namespace
                              :name app-name}
                   :spec {:selector app-labels
                          :ports [{:port 80 :targetPort image-port}]}}
        final-args (deep-merge base-args service-options)]
    (new-resource (.. k8s -core -v1 -Service) app-name final-args provider dependencies)))

(defn create-chart [provider app-namespace app-name dependencies chart-options]
  (let [base-args {:chart     app-name
                   :namespace app-namespace}
        final-args (deep-merge base-args chart-options)]
    (new-resource (.. k8s -helm -v3 -Chart) app-name final-args provider dependencies)))

(defn create-ingress [provider app-namespace app-name host image-port dependencies ingress-options]
  (let [base-args {:metadata {:name app-name
                              :namespace app-namespace
                              :annotations {"caddy.ingress.kubernetes.io/snippet"
                                            (str "tls {\n  dns cloudflare {env.CLOUDFLARE_API_TOKEN}\n}\n" (:caddy-snippet ingress-options))}}
                   :spec
                   {:ingressClassName "caddy"
                    :rules [{:host host
                             :http {:paths [{:path "/"
                                             :pathType "Prefix"
                                             :backend {:service {:name (or (:service-name ingress-options) app-name)
                                                                 :port {:number image-port}}}}]}}]}}
        final-args (deep-merge base-args ingress-options)]
    (new-resource (.. k8s -networking -v1 -Ingress) app-name final-args provider dependencies)))

(defn create-storage-class [provider app-name dependencies storage-options]
  (let [base-args {:metadata {:name app-name}}
        final-args (deep-merge base-args storage-options)]
    (new-resource (.. k8s -storage -v1 -StorageClass) app-name final-args provider dependencies)))

(defn deploy-stack
  "Deploys a versatile stack of K8s resources, including optional Helm charts."
  [& args]
  (let [[component-kws [options]] (split-with keyword? args)
        requested-components (set component-kws)

        {:keys [provider vault-provider pulumi-cfg app-namespace app-name image image-port vault-load-yaml exec-fn
                storage-class-opts secret-opts ns-opts image-opts ingress-opts service-opts deployment-opts chart-opts]
         :or {vault-load-yaml false image-port 80}} options
        app-labels {:app app-name}

        prepared-vault-data (when (requested-components :vault-secrets)
                              (vault-utils/prepare {:provider       provider
                                                    :vault-provider vault-provider
                                                    :app-name       app-name
                                                    :app-namespace  app-namespace
                                                    :load-yaml      vault-load-yaml}))

        {:keys [secrets yaml-values bind-secrets]} (or prepared-vault-data {:secrets nil :yaml-values nil :bind-secrets nil})

        host (when secrets (.apply secrets #(aget % "host")))

        ns (when (requested-components :namespace)
             (create-namespace provider app-namespace nil ns-opts))

        docker-image (when (requested-components :docker-image)
                       (create-image app-name image-opts))

        secret (when (requested-components :secret)
                 (create-secret provider app-namespace app-name nil secret-opts))

        storage-class (when (requested-components :storage-class)
                        (create-storage-class provider app-name nil storage-class-opts))

        image (if (some? image) image (str (-> cfg :docker-repo) "/" app-name ":latest"))

        chart (when (requested-components :chart)
                (create-chart provider app-namespace app-name (vec (filter some? [ns docker-image bind-secrets]))
                              (let [helm-values-fn (get chart-opts :helm-values-fn (fn [ctx] (:base-values ctx)))
                                    context {:base-values  yaml-values
                                             :secrets      (if (some? prepared-vault-data) secrets nil)
                                             :app-name     app-name}
                                    calculated-values (helm-values-fn context)
                                    transformations-fn (if (get chart-opts :transformations) [(get chart-opts :transformations)] [])]
                                (-> chart-opts
                                    (assoc :values calculated-values)
                                    (assoc :transformations transformations-fn)))))

        deployment (when (requested-components :deployment)
                     (create-deployment provider app-namespace app-name app-labels image image-port (vec (filter some? [ns docker-image bind-secrets])) deployment-opts))

        service (when (requested-components :service)
                  (create-service provider app-namespace app-name app-labels image-port [deployment] service-opts))

        app-dependency (or service chart bind-secrets)

        ingress (when (requested-components :ingress) (create-ingress provider app-namespace app-name host image-port [app-dependency] ingress-opts))

        execute (when (requested-components :execute)
                  (exec-fn (assoc options :dependencies  (vec (filter some? [chart ns secret storage-class deployment service ingress docker-image])))))]



    {:namespace ns, :vault-secrets prepared-vault-data, :secret secret, :docker-image docker-image :storage-class storage-class, :chart chart, :deployment deployment, :service service, :ingress ingress :execute execute}))