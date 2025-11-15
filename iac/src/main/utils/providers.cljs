(ns utils.providers
  (:require
   ["@pulumi/pulumi" :as pulumi] ["@pulumi/vault" :as vault] ["@pulumiverse/harbor" :as harbor] ["@pulumi/kubernetes" :as k8s]
   [clojure.string :as str] [clojure.walk :as walk]
   [utils.general :refer [resolve-template]]
   [utils.k8s :as k8s-utils]
   [utils.harbor :as harbor-utils]
   [utils.docker :as docker-utils] [utils.vault :as vault-utils]
   [utils.stack-processor :refer [deploy! component-specs]]))

(defn resolve-provider-template [constructor name config]
  {:constructor constructor
   :name name
   :config config})

(def provider-templates
  (into {} (map (fn [[k v]] [k (apply resolve-provider-template (vals v))])
                {:vault vault-utils/provider-template
                 :harbor harbor-utils/provider-template
                 :k8s k8s-utils/provider-template})))

(defn get-provider-outputs-config []
  {:vault  {:stack :init
            :outputs ["vaultAddress" "vaultToken"]}
   #_:harbor #_{:stack :shared
                :outputs ["username" "password" "url"]}
   :k8s   {:stack :init
           :outputs ["kubeconfig"]}})

(defn get-stack-refs []
  {:init (new pulumi/StackReference "init")
   #_:shared #_(new pulumi/StackReference "shared")})

(defn extract-expanded-keywords [stack]
  (let [expand-chain
        (fn [chain]
          (when (and (sequential? chain) (keyword? (first chain)))
            (let [ns (or (namespace (first chain)) (name (first chain)))]
              (map #(keyword ns (name %)) (rest chain)))))]

    (mapcat (fn [item]
              (cond
                (and (sequential? item) (keyword? (first item)))
                (expand-chain item)
                (keyword? item)
                [item]
                :else
                nil))
            stack)))



(defn get-all-providers [service-registry]
  (->> service-registry
       (mapcat (comp extract-expanded-keywords :stack))

       (map (fn [component-key]
              (if-let [ns (namespace component-key)]
                (keyword ns)
                (let [k-name (name component-key)
                      parts (str/split k-name #":")]
                  (when (> (count parts) 1)
                    (keyword (first parts)))))))
       (remove nil?)
       (into #{})
       vec))

(def provider-rules
  {:k8s k8s-utils/pre-deploy-rule})


(defn provider-apply [service-registry pulumi-cfg]
  (let [providers-needed (get-all-providers service-registry)
        provider-outputs-config (get-provider-outputs-config)
        stack-refs (get-stack-refs)
        needed-output-configs (select-keys provider-outputs-config providers-needed)
        outputs-to-fetch (reduce-kv
                          (fn [acc _provider-key data]
                            (let [stack-key (:stack data)
                                  stack-ref (get stack-refs stack-key)
                                  outputs (:outputs data)]

                              (reduce
                               (fn [m output-name]
                                 (assoc m (keyword output-name) (.getOutput stack-ref output-name)))
                               acc
                               outputs)))
                          {}
                          needed-output-configs)

        all-provider-inputs (pulumi/all (clj->js outputs-to-fetch))]

    (.apply all-provider-inputs
            (fn [values]
              (js/Promise.
               (fn [resolve _reject]
                 (let [resolved-outputs (js->clj values :keywordize-keys true)
                       instantiated-providers
                       (reduce
                        (fn [acc provider-key]
                          (if-let [template (get provider-templates provider-key)]
                            (let [constructor (:constructor template)
                                  provider-name (:name template)
                                  resolved-config (resolve-template (:config template) {} resolved-outputs)]

                              (assoc acc provider-key (new constructor provider-name (clj->js resolved-config))))
                            acc))
                        {}
                        providers-needed)
                       pre-deploy-results
                       (reduce-kv
                        (fn [acc provider-key provider-instance]
                          (if-let [rule-fn (get provider-rules provider-key)]
                            (let [rule-results (rule-fn {:service-registry service-registry
                                                         :provider provider-instance})]
                              (assoc acc provider-key rule-results))
                            acc))
                        {}
                        instantiated-providers)]
                   (resolve
                    (deploy!
                     {:pulumi-cfg pulumi-cfg
                      :service-registry service-registry
                      :all-providers instantiated-providers
                      :pre-deploy-deps pre-deploy-results})))))))))