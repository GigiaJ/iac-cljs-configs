(ns utils.k8s
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/vault" :as vault]
   [utils.vault :as vault-utils]
   [clojure.string :as str]
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



(defn deploy-stack
  "Deploys a versatile stack of K8s resources, including optional Helm charts."
  [& args]
  (let [[component-kws [options]] (split-with keyword? args)
        requested-components (set component-kws)

        {:keys [provider vault-provider app-namespace app-name hostname image image-port caddy-snippet vault-load-yaml chart-repo transformations helm-values-fn]
         :or {vault-load-yaml false image-port 80 caddy-snippet "" helm-values-fn #(:base-values %)}} options
        app-labels {:app app-name}


        full-snippet (str "tls {\n  dns cloudflare {env.CLOUDFLARE_API_TOKEN}\n}\n" caddy-snippet)



        ns  (when (requested-components :namespace)
              ;;(try
                (new (.. k8s -core -v1 -Namespace) app-namespace
                     (clj->js {:metadata {:name app-namespace}})
                     (clj->js {:provider provider}))
                #_(catch js/Error _
                (.get (.. k8s -core -v1 -Namespace) (str "ns-" app-name)
                      app-namespace
                      (clj->js {:provider provider})))
        );;)



        prepared-vault-data (when (requested-components :vault-secrets)
                              (vault-utils/prepare {:provider       provider
                                                    :vault-provider vault-provider
                                                    :app-name       app-name
                                                    :app-namespace  app-namespace
                                                    :load-yaml      vault-load-yaml}))

        {:keys [helm-v3 secrets yaml-values bind-secrets]} prepared-vault-data


        helm-fn (helm-values-fn {:base-values  yaml-values
                                 :secrets      secrets
                                 :app-name    app-name})

        host (.apply secrets #(aget % "host"))

        chart (when (requested-components :chart)
                (new (.. helm-v3 -Chart) app-name
                     (clj->js {:chart     app-name
                               :fetchOpts {:repo chart-repo}
                               :namespace app-namespace
                               :values helm-fn})
                     (clj->js {:provider provider
                               :enableServerSideApply false
                               :dependsOn (vec (filter identity [ns bind-secrets]))
                               :transformations (vec (filter identity [transformations]))})))

        deployment (when (requested-components :deployment)
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
                          (clj->js {:provider provider :dependsOn [ns]})))

        service (when (requested-components :service)
                  (new (.. k8s -core -v1 -Service) app-name
                       (clj->js {:metadata {:namespace app-namespace
                                            :name app-name}
                                 :spec {:selector app-labels
                                        :ports [{:port 80 :targetPort image-port}]}})
                       (clj->js {:provider provider :dependsOn [deployment]})))
        app-dependency (or service chart bind-secrets)

        ingress (when (requested-components :ingress)
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
                       (clj->js {:provider provider :dependsOn [app-dependency]})))]

    {:namespace ns, :vault-secrets prepared-vault-data, :chart chart, :deployment deployment, :service service, :ingress ingress}))