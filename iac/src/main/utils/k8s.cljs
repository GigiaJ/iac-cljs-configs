(ns utils.k8s
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   [utils.vault :as vault-utils]
   ["@pulumi/docker" :as docker] 
   ["path" :as path]
   [clojure.walk :as walk]
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

(defn make-paths [& path-groups]
  (mapcat (fn [{:keys [paths backend]}]
            (mapv (fn [p]
                    {:path p
                     :pathType "Prefix"
                     :backend {:service backend}})
                  paths))
          path-groups))

(defn generic-make-transformer
  "Returns a Pulumi-compatible transformer that unwraps Output values via .apply."
  [f {:keys [secrets base-values]}]
  (.apply secrets
          (fn [smap]
            (let [m (js->clj smap :keywordize-keys true)
                  updates (f {:function-keys m})
                  result (clj->js (deep-merge base-values updates))]
              result))))

(defn safe-parse-int [s]
  (let [n (js/parseInt s 10)]
    (if (js/isNaN n) nil n)))

(defn string->int? [s]
  (and (string? s)
       (re-matches #"^-?\d+$" s)))

(defn- coerce-value [v]
  (if (string->int? v)
    (safe-parse-int v)
    v))

;; Whitelist functions for resolving templates. Intended to be extended.
(def ^:private safe-fns
  {'str str
   'make-paths make-paths})

(defn resolve-template [template values secondary-values]
  (walk/postwalk
   (fn [x]
     (cond
       (and (list? x) (contains? safe-fns (first x)))
       (apply (get safe-fns (first x)) (rest x))
       (symbol? x)
       (if (contains? safe-fns x)
         x
         (let [kw (keyword x)]
           (cond
             (contains? values x) (coerce-value (get values x))
             (contains? values kw) (coerce-value (get values kw))
             (contains? secondary-values x) (coerce-value (get secondary-values x))
             (contains? secondary-values kw) (coerce-value (get secondary-values kw))
             :else x)))
       :else x))
   template))

(defn generic-transform
  "Takes a creator function and executes it with resolved arguments,
   handling asynchronicity when secrets are present."
  [creator-fn opts base-values secrets options]
  (if (nil? secrets)
    (let [final-args (clj->js (deep-merge base-values (resolve-template opts {} options)))]
      (creator-fn final-args))
    (.apply secrets
            (fn [smap]
              (let [m (js->clj smap :keywordize-keys true)
                    final-args (clj->js (deep-merge base-values (resolve-template opts m options)))]
                (creator-fn final-args))))))

(defn new-resource [resource-type resource-name final-args provider dependencies]
  (new resource-type resource-name
       (clj->js final-args)
       (clj->js {:provider            provider
                 :enableServerSideApply false
                 :dependsOn           dependencies})))


(defn default-ingress [{:keys [app-name app-namespace host image-port ingress-opts]}]
  {:metadata {:name app-name
              :namespace app-namespace
              :annotations {"caddy.ingress.kubernetes.io/snippet"
                            (str "tls {\n  issuer cloudflare\n  dns cloudflare {env.CLOUDFLARE_API_TOKEN}\n}\n"
                                 (:caddy-snippet ingress-opts))}}
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

(defn default-service [{:keys  [app-name app-namespace app-labels image-port]}]
  {:metadata {:namespace app-namespace
              :name app-name}
   :spec {:selector app-labels
          :ports [{:port 80 :targetPort image-port}]}})

(defn default-storage-class [{:keys [app-name]}]
  {:metadata {:name app-name}})



(defn create-ingress [provider app-name dependencies ingress-opts]
  (new-resource (.. k8s -networking -v1 -Ingress) app-name ingress-opts provider dependencies))

(defn create-secret [provider app-name dependencies secret-opts]
    (new-resource (.. k8s -core -v1  -Secret) app-name secret-opts provider dependencies))

(defn create-image [app-name image-opts] 
    (new (.. docker -Image) app-name (clj->js image-opts)))

(defn create-namespace [provider app-namespace dependencies ns-options] 
    (new-resource (.. k8s -core -v1 -Namespace) app-namespace ns-options provider dependencies))

(defn create-deployment [provider app-name dependencies deployment-opts]
    (new-resource (.. k8s -apps -v1 -Deployment) app-name deployment-opts provider dependencies))

(defn create-service [provider app-name dependencies service-opts]
    (new-resource (.. k8s -core -v1 -Service) app-name service-opts provider dependencies))

(defn create-chart [provider app-name dependencies chart-opts] 
    (new-resource (.. k8s -helm -v3 -Chart) app-name chart-opts provider dependencies))



(defn create-storage-class [provider app-name dependencies storage-opts]
    (new-resource (.. k8s -storage -v1 -StorageClass) app-name storage-opts provider dependencies))




(defn deploy-stack
  "Deploys a versatile stack of K8s resources, including optional Helm charts."
  [& args]
  (let [[component-kws [options]] (split-with keyword? args)
        requested-components (set component-kws)

        {:keys [provider vault-provider pulumi-cfg app-namespace app-name image image-port vault-load-yaml exec-fn
                storage-class-opts secret-opts ns-opts image-opts ingress-opts service-opts deployment-opts chart-opts]
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

        image (if (some? image) image (str (-> cfg :docker-repo) "/" app-name ":latest"))

        ns
        (when (requested-components :namespace)
          (generic-transform
           (fn [final-args]
             (create-namespace provider app-namespace nil final-args))
           ns-opts
           (default-namespace options)
           secrets
           options))

        docker-image
        (when (requested-components :docker-image)
          (generic-transform
           (fn [final-args]
             (create-image app-name final-args))
           image-opts
           (default-image options)
           secrets
           options))

        secret
        (when (requested-components :secret)
          (generic-transform
           (fn [final-args]
             (create-secret provider app-name nil
                            final-args))
           storage-class-opts
           (default-secret options)
           secrets
           options))

        storage-class
        (when (requested-components :storage-class)
          (generic-transform
           (fn [final-args]
             (create-storage-class provider app-name nil
                                   final-args))
           storage-class-opts
           (default-storage-class options)
           secrets
           options))

        chart (when (requested-components :chart)
                (let [chart-base-values (deep-merge (default-chart options)
                                                    (update-in chart-opts [:values] #(deep-merge % (or yaml-values {}))))]
                  (generic-transform
                   (fn [final-args]
                     (create-chart provider app-name
                                   (vec (filter some? [ns docker-image bind-secrets]))
                                   final-args))
                   chart-opts
                   chart-base-values
                   secrets
                   options)))

        deployment
        (when (requested-components :deployment)
          (generic-transform
           (fn [final-args]
             (create-deployment provider app-name
                                (vec (filter some? [ns docker-image bind-secrets]))
                                final-args))
           deployment-opts
           (default-deployment options)
           secrets
           options))

        service
        (when (requested-components :service)
          (generic-transform
           (fn [final-args]
             (create-service provider app-name
                             (vec (filter some? [ns deployment bind-secrets]))
                             final-args))
           service-opts
           (default-service options)
           secrets
           options))

        ingress (when (requested-components :ingress)
                  (generic-transform
                   (fn [final-args]
                     (create-ingress provider app-name
                                     (vec (filter some? [service chart bind-secrets]))
                                     final-args))
                   ingress-opts
                   (default-ingress (assoc options :host host))
                   secrets
                   options))
        
        execute (when (requested-components :execute)
                  (exec-fn (assoc options :dependencies  (vec (filter some? [chart ns secret storage-class deployment service ingress docker-image])))))]



    {:namespace ns, :vault-secrets prepared-vault-data, :secret secret, :docker-image docker-image :storage-class storage-class, :chart chart, :deployment deployment, :service service, :ingress ingress :execute execute}))