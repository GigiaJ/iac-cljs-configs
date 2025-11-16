(ns utils.stack-processor
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   ["@pulumiverse/harbor" :as harbor]
   [utils.defaults :as default]
   [utils.vault :as vault-utils]
   [utils.general :refer [deep-merge new-resource  component-factory resource-factory deploy-stack-factory iterate-stack]]
   ["@pulumi/docker" :as docker]
   ["@pulumi/docker-build" :as docker-build]
   [clojure.walk :as walk]
   [clojure.string :as str]
   ["path" :as path]
   [configs :refer [cfg]]
   [utils.k8s :as k8s-utils]
   [utils.harbor :as harbor-utils]
   [utils.docker :as docker-utils])
  (:require-macros [utils.general :refer [build-registry]]))


#_(def component-specs-defs
  {:k8s k8s-utils/component-specs-defs
   :harbor harbor-utils/component-specs-defs
   :docker docker-utils/component-specs-defs})

#_(def component-specs (build-registry component-specs-defs))

(defn make-paths [& path-groups]
  (mapcat (fn [{:keys [paths backend]}]
            (mapv (fn [p]
                    {:path p
                     :pathType "Prefix"
                     :backend {:service backend}})
                  paths))
          path-groups))


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
   'b64e (fn [s] (-> (.from js/Buffer s) (.toString "base64")))
   'println #(js/console.log %)
   'make-paths make-paths})

(defn- is-output? [x] (some? (.-__pulumiOutput x)))

(defn resolve-template
  [template secrets-map options-map]
  (let [data (merge (js->clj options-map) (js->clj secrets-map))]
    (walk/postwalk
     (fn [x]
       (cond
         (and (list? x) (contains? safe-fns (first x)))
         (let [f (get safe-fns (first x))
               args (rest x)]
           (if (some is-output? args)
             (.apply (pulumi/all (clj->js args))
                     (fn [resolved-args]
                       (apply f (js->clj resolved-args))))
             (apply f args)))
         (and (list? x) (symbol? (first x)))
         (cond
           (= (first x) '->)
           (let [[_ resource-key & steps] x
                 resource (get data resource-key)]
             (if-not resource x
                     (reduce (fn [acc step]
                               (cond
                                 (and (symbol? step) (str/starts-with? (name step) ".-"))
                                 (let [prop-name (subs (name step) 2)]
                                   (aget acc prop-name))
                                 (and (list? step) (= (first step) 'get))
                                 (get acc (second step) (nth step 2 nil))
                                 :else acc))
                             resource
                             steps)))

           (= (first x) 'get) (get data (second x) (nth x 2 nil))
           (= (first x) 'get-in) (get-in data (second x) (nth x 2 nil))
           :else x)
         (symbol? x)
         (if (contains? safe-fns x)
           x
           (let [kw (keyword x)]
             (cond
               (contains? data x) (coerce-value (get data x))
               (contains? data kw) (coerce-value (get data kw))
               :else x)))
         :else x))
     template)))


(defn generic-transform
  "Takes a creator function and executes it with resolved arguments,
   handling asynchronicity when secrets are present."
  [creator-fn opts base-values secrets options]
  (if (nil? secrets)
    (let [final-args (clj->js (deep-merge base-values (resolve-template opts {} options)))]
      (pulumi/output (creator-fn final-args)))
    (.apply secrets
            (fn [smap]
              (let [m (js->clj smap :keywordize-keys true)
                    final-args (clj->js (deep-merge base-values (resolve-template opts m options)))]
                (creator-fn final-args))))))

(def component-specs
  {:vault {:provider-key :vault
           :provider-deps [:k8s]}
   ;; K8s Resources 
   :k8s:namespace {:constructor (.. k8s -core -v1 -Namespace)
                   :provider-key :k8s
                   :defaults-fn (fn [env] ((get-in default/defaults [:k8s :namespace]) (:options env)))}

   :k8s:secret {:constructor (.. k8s -core -v1 -Secret)
                :provider-key :k8s
                :defaults-fn (fn [env] ((get-in default/defaults [:k8s :secret]) (:options env)))}
   
   :k8s:config-map {:constructor (.. k8s -core -v1 -ConfigMap)
                    :provider-key :k8s
                    :defaults-fn (fn [env] ((get-in default/defaults [:k8s :config-map]) (:options env)))}

   :k8s:deployment {:constructor (.. k8s -apps -v1 -Deployment)
                    :provider-key :k8s
                    :defaults-fn (fn [env] ((get-in default/defaults [:k8s :deployment]) (:options env)))}

   :k8s:service {:constructor (.. k8s -core -v1 -Service)
                 :provider-key :k8s
                 :defaults-fn (fn [env] ((get-in default/defaults [:k8s :service]) (:options env)))}

   :k8s:ingress {:constructor (.. k8s -networking -v1 -Ingress)
                 :provider-key :k8s
                 :defaults-fn (fn [env] ((get-in default/defaults [:k8s :ingress]) (:options env)))}

   :k8s:chart {:constructor (.. k8s -helm -v3 -Chart)
               :provider-key :k8s
               :defaults-fn (fn [env] 
                              (deep-merge ((get-in default/defaults [:k8s :chart]) (:options env))
                                          (update-in (get-in (:options env) [:k8s:chart-opts]) [:values]
                                                     #(deep-merge % (or (:yaml-values (:options env)) {})))))}
   :k8s:storage-class {:constructor (.. k8s -storage -v1 -StorageClass)
                   :provider-key :k8s
                   :defaults-fn (fn [env] ((get-in default/defaults [:k8s :storage-class]) (:options env)))}

   ;; Docker Resources
   :docker:image {:constructor (.. docker-build -Image)
                  :provider-key :docker
                  :defaults-fn (fn [env] ((get-in default/defaults [:docker :image]) (:options env)))}

   ;; Harbor Resources
   :harbor:project {:constructor (.. harbor -Project)
                    :provider-key :harbor
                    :defaults-fn (fn [env] ((get-in default/defaults [:harbor :project]) (:options env)))}
   
   :harbor:robot-account {:constructor (.. harbor -RobotAccount)
                          :provider-key :harbor
                          :defaults-fn (fn [env] ((get-in default/defaults [:harbor :robot-account]) (:options env)))}})

(defmulti deploy-resource
  "Generic resource deployment multimethod.
  Dispatches on the fully-qualified resource keyword.
  Returns a map of {:resource (the-pulumi-resource) :common-opts-update {map-of-new-state}}."
  (fn [dispatch-key _config] dispatch-key))

(defmethod deploy-resource :default
  [dispatch-key
   full-config]

  (if-let [spec (get component-specs dispatch-key)]
    (let [app-name       (:app-name full-config)
          dependsOn      (:dependsOn full-config)
          provider-key   (:provider-key spec)
          provider       (get full-config provider-key)
          opts-key       (keyword (str (name dispatch-key) "-opts"))
          component-opts (get full-config opts-key)
          env            {:options full-config :secrets (:secrets full-config)}
          defaults       (when-let [defaults-fn (:defaults-fn spec)]
                           (defaults-fn env)) 
          resource-class (:constructor spec)]
      
      (if resource-class
        (let [creator-fn (fn [final-args]
                           (new-resource resource-class
                                         app-name
                                         final-args
                                         provider
                                         dependsOn))
              resource (generic-transform creator-fn component-opts defaults (:secrets env) full-config)]

          {:resource resource})
        (throw (js/Error. (str "No :constructor found for spec: " dispatch-key)))))

    (throw (js/Error. (str "Unknown resource: " dispatch-key)))))

(defmethod deploy-resource :vault:prepare
  [_ config]
  (let [prepare-opts (get config :vault:prepare-opts {})
        defaults {:provider       (:k8s config)
                  :vault-provider (:vault config)
                  :app-name       (:app-name config)
                  :app-namespace  (:app-namespace config)
                  :load-yaml      (get config :vault-load-yaml false)}
        final-args (merge defaults prepare-opts)
        
        prepared-vault-data (try
                              (vault-utils/prepare final-args)
                              (catch js/Error e
                                (js/console.error "!!! Error in :vault:prepare :" e)
                                nil))]
    {:common-opts-update prepared-vault-data
     :resource (:bind-secrets prepared-vault-data)}))

(defmethod deploy-resource :vault:retrieve
  [_ config]
  (let [retrieve-opts (get config :vault:retrieve-opts {})
        defaults {:vault-provider (:vault config)
                  :app-name       (:app-name config)
                  :app-namespace  (:app-namespace config)}
        final-args (merge defaults retrieve-opts)
        retrieved-data (try
                         (vault-utils/retrieve (:vault-provider final-args)
                                               (:app-name final-args)
                                               (:app-namespace final-args))
                         (catch js/Error e
                           (js/console.error " Error in :vault:retrieve :" e)
                           nil))]
    {:common-opts-update retrieved-data}))

;; https://www.pulumi.com/docs/iac/concepts/resources/dynamic-providers/
(defmethod deploy-resource :generic:execute
  [_ full-config]
  (let [app-name       (:app-name full-config)
        dependsOn      (:dependsOn full-config)
        component-opts (assoc (:execute-opts full-config)
                              :pulumi-cfg (:pulumi-cfg full-config)
                              :secrets (:secrets full-config)
                              )
        defaults      {}
        exec-fn        (:exec-fn full-config)
        resource-id    (str app-name "-exec")
        provider #js {:create (fn [inputs-js]
                                (js/Promise.
                                 (fn [resolve _reject]
                                   (resolve
                                    #js {:id resource-id
                                         :outs inputs-js}))))
                      :delete (fn [id old-inputs-js]
                                (js/Promise.resolve))
                      :update (fn [id old-inputs-js new-inputs-js]
                                (js/Promise.
                                 (fn [resolve _reject]
                                     (resolve #js {:outs new-inputs-js}))))}
        gen (generic-transform #(clj->js (exec-fn (js->clj % :keywordize-keys true))) component-opts defaults (:secrets full-config) full-config)
        creator-fn (fn [inputs]
                     (pulumi/dynamic.Resource.
                      provider
                      resource-id
                      inputs
                      (clj->js {:dependsOn (vec dependsOn)})))
        resource (.apply gen #(creator-fn %))]
    {:resource resource}))


(defn handle-keyword-item [last-resource item config common-opts]
  (let [dispatch-key item
        depends-on (when last-resource [last-resource])
        final-config (merge config common-opts {:dependsOn depends-on}) 
        result-map (deploy-resource dispatch-key final-config)
        resource (:resource result-map)
        opts-update (:common-opts-update result-map)
        resource-update (when resource {dispatch-key resource})]

    [resource
     resource-update
     (merge common-opts opts-update resource-update)]))

(defn handle-list-item [last-resource item config common-opts]
  (let [provider-key (first item)
        resource-keys (rest item)

        nested-result
        (reduce
         (fn [nested-acc resource-key]
           (let [inner-last-resource (get nested-acc :last-resource)
                 inner-resources-map (get nested-acc :resources)
                 inner-common-opts   (get nested-acc :common-opts)
                 dispatch-key (keyword (str (name provider-key) ":" (name resource-key)))
                 [new-resource new-resource-map new-common-opts]
                 (handle-keyword-item inner-last-resource dispatch-key config inner-common-opts)]
             {:last-resource (or new-resource inner-last-resource)
              :resources     (merge inner-resources-map new-resource-map)
              :common-opts   new-common-opts}))
         {:last-resource last-resource
          :resources     {}
          :common-opts   common-opts}

         resource-keys)]

    [(:last-resource nested-result)
     (:resources nested-result)
     (:common-opts nested-result)]))

(defn process-stack
  "Recursively processes a stack configuration, building a dependency chain.
  Returns a map of all created resources keyed by their dispatch keyword."
  [stack-items config initial-common-opts]
  (let [result
        (reduce
         (fn [acc item]
           (let [{:keys [last-resource common-opts]} acc
                 [new-resource new-resources new-common-opts]
                 (if (keyword? item)
                   (handle-keyword-item last-resource item config common-opts)
                   (handle-list-item last-resource item config common-opts))]
             {:last-resource (or new-resource last-resource)
              :resources-map (merge (:resources-map acc) new-resources)
              :common-opts new-common-opts}))
         {:last-resource nil
          :resources-map {}
          :common-opts initial-common-opts}
         stack-items)]
    (:resources-map result)))

(defn deploy! [{:keys [pulumi-cfg service-registry all-providers]}]
  (let [

        deployment-results
        (into
         {}
         (for [config service-registry]
           (let [
                 {:keys [stack app-name]} config
                 _ (when (nil? config)
                     (throw (js/Error. "Service registry contains a nil value!")))

                 common-opts (merge
                              all-providers
                              (select-keys config [:app-name :app-namespace])
                              {:pulumi-cfg pulumi-cfg})
                 ]

             [app-name (process-stack stack config common-opts)])))
         ]
    (clj->js deployment-results)))

