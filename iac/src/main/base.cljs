(ns base
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   ["@pulumi/kubernetes" :as k8s]
   [infra.init :as infra] 
   [service-registries :refer [base-service-registry shared-service-registry deployment-service-registry]]
   [utils.k8s :refer [create-namespace deploy-stack]]))

(defn deploy! [{:keys [provider vault-provider pulumi-cfg service-registry namespaces?]}]
  (let [namespaces (->> service-registry (remove #(contains? % :no-namespace)) (map :app-namespace) (set))
        _ (when namespaces? (doseq [namespace namespaces] (create-namespace provider namespace nil nil)))
        deployment-results
        (into
         {}
         (for [config service-registry]
           (let [{:keys [stack app-name]} config]
             [app-name (apply deploy-stack (conj (vec stack) (merge config {:provider provider
                                                                            :vault-provider vault-provider
                                                                            :pulumi-cfg pulumi-cfg})))])))]
    (clj->js deployment-results)))

(defn apps [stack-ref pulumi-cfg provider service-registry]
  (let [vault-output (pulumi/output
                      (clj->js {:address (.getOutput stack-ref "vaultAddress")
                                :token   (.getOutput stack-ref "vaultToken")}))
        vault-provider (new vault/Provider
                            "vault-provider"
                            (clj->js vault-output))]
    (deploy! {:provider provider
              :vault-provider vault-provider
              :pulumi-cfg pulumi-cfg
              :service-registry service-registry
              :namespaces? true})))

(defn if-no-configs [configs then-fn & [else-fn]]
  (if (nil? configs)
    (then-fn)
    (if else-fn (else-fn) nil)))

(defn initialize [configs]
  (let [pulumi-cfg  (pulumi/Config.)
        stack-ref (new pulumi/StackReference "init")
        kubeconfig (if-no-configs configs #(infra/create-cluster pulumi-cfg) #(.getOutput stack-ref "kubeconfig"))
        setup (.apply kubeconfig
                      (fn [kc]
                        (js/Promise.
                         (fn [resolve _reject]
                           (let [provider (new k8s/Provider
                                               "k8s-dynamic-provider"
                                               (clj->js {:kubeconfig kc}))] 
                             (resolve
                              (if-no-configs
                               configs
                               #(deploy! {:provider provider
                                          :vault-provider nil 
                                          :pulumi-cfg pulumi-cfg 
                                          :service-registry base-service-registry
                                          :namespaces? false})
                               #(apps stack-ref pulumi-cfg provider configs)
                              )))))))]
    {:kubeconfig kubeconfig :setup setup}))

(defn build-exports [init]
  (let [kubeconfig (get init :kubeconfig)
        app-outputs (get init :setup)]
    {:kubeconfig   kubeconfig
     :vaultAddress (.apply app-outputs #(-> % .-openbao .-execute .-address))
     :vaultToken   (.apply app-outputs #(aget (-> % .-openbao .-execute) "root-token"))}))

(defn extended-exports [init]
  (let [;;exports (base.build-exports init)
        app-outputs (get init :setup)]
    #_(assoc exports :nextcloudUrl (.apply app-outputs #(get-in % [:nextcloud :nextcloud-url])))))

(defn quick-deploy [configs exports]
  (-> 
   (initialize configs)
   (exports)
   (clj->js)))

(defn quick-deploy-base []
  (quick-deploy nil build-exports))

(defn quick-deploy-shared []
  (base/quick-deploy shared-service-registry extended-exports))

(defn quick-deploy-services []
  (base/quick-deploy deployment-service-registry extended-exports))

