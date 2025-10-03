(ns base
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/kubernetes" :as k8s]
   [infra.init :as infra]
   [k8s.add-ons.csi-driver.hetzner :as hetzner-csi]
   [k8s.add-ons.ingress-controller.caddy :as caddy]
   [infra.openbao :as openbao]))

(defn if-no-apps [apps then-fn & [else-fn]]
  (if (nil? apps)
    (then-fn)
    (if else-fn (else-fn) nil)))



(defn initialize [apps]
  (let [cfg  (pulumi/Config.)
        stack-ref (new pulumi/StackReference "init")
        kubeconfig (if-no-apps apps #(infra/create-cluster cfg) #(.getOutput stack-ref "kubeconfig"))
        setup (.apply kubeconfig
                      (fn [kc]
                        (js/Promise.
                         (fn [resolve _reject]
                           (let [provider (new k8s/Provider
                                               "k8s-dynamic-provider"
                                               (clj->js {:kubeconfig kc}))] 
                             (resolve
                              (if-no-apps
                               apps
                               #(let [vault-result (openbao/deploy provider)
                                     caddy-result (caddy/deploy provider)
                                     csi-result   (hetzner-csi/deploy provider)] 
                                 {:vault vault-result
                                  :caddy caddy-result
                                  :csi   csi-result})
                               #(apps stack-ref cfg provider)
                              )))))))]
    {:kubeconfig kubeconfig :setup setup}))

(defn build-exports [init]
  (let [kubeconfig (get init :kubeconfig)
        app-outputs (get init :setup)]
    {:kubeconfig   (get kubeconfig :kubeconfig)
     :vaultAddress (pulumi/output (.apply app-outputs #(get-in % [:vault :address])))
     :vaultToken   (pulumi/output (.apply app-outputs #(get-in % [:vault :root-token])))}))


(defn quick-deploy []
  (->
   (initialize nil)
   (build-exports)
   (clj->js)))

(defn deploy-core []
  (let [init (initialize nil)]
    (set! (.-exports js/module)
          (clj->js (build-exports init)))))