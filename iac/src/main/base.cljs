(ns base
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   ["@pulumi/kubernetes" :as k8s]
   [infra.init :as infra] 
   [infra.openbao :as openbao]
   [k8s.add-ons.csi-driver.hetzner :as hetzner-csi]
   [utils.k8s :refer [create-ns deploy-stack]]))

(defn deploy! [{:keys [provider vault-provider pulumi-cfg service-registry namespaces?]}]
  (let [namespaces (->> service-registry (map :app-namespace) (set))
        _ (when namespaces? (doseq [namespace namespaces] (create-ns provider namespace)))
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
                                          :service-registry [hetzner-csi/config openbao/config] 
                                          :namespaces? false})
                               #(apps stack-ref pulumi-cfg provider configs)
                              )))))))]
    {:kubeconfig kubeconfig :setup setup}))

(defn build-exports [init]
  (let [kubeconfig (get init :kubeconfig)
        app-outputs (get init :setup)]
    {:kubeconfig   (get kubeconfig :kubeconfig)
     :vaultAddress (.apply app-outputs #(-> % .-openbao .-execute .-address))
     :vaultToken   (.apply app-outputs #(aget (-> % .-openbao .-execute) "root-token"))}))

(defn quick-deploy [configs exports]
  (-> 
   (initialize configs)
   (exports)
   (clj->js)))

(defn quick-deploy-base []
  (quick-deploy nil build-exports))