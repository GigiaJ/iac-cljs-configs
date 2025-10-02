(ns k8s.add-ons.ingress-controller.caddy
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/docker" :as docker]
   [configs :refer [cfg]]
   ["path" :as path]
   [clojure.string :as str]))

(defn deploy [provider]
  (let [context-path (.. path (join "." (-> cfg :resource-path)))
        dockerfile-path (.. path (join context-path "caddy.dockerfile"))
        custom-caddy-image (new (.. docker -Image) "caddy"
                                (clj->js {:build {:context context-path
                                                  :dockerfile dockerfile-path}
                                          :imageName (str (-> cfg :docker-repo) "/caddy:latest")}))

        helm-v3 (.. k8s -helm -v3)
        service-name "caddy-ingress-controller"
        namespace "caddy-system"
        ns (new (.. k8s -core -v1 -Namespace) "caddy-ns"
                (clj->js {:metadata {:name namespace}})
                (clj->js {:provider provider}))

        helm-values (.apply (.-imageName custom-caddy-image)
                            (fn [image-name]
                              (let [parts (str/split image-name #":")
                                    repo (first parts)
                                    tag (or (second parts) "latest")]
                                (clj->js
                                 {:ingressController
                                  {:deployment {:kind "DaemonSet"}
                                   :daemonSet {:useHostPort true}

                                   :ports {:web {:hostPort 80}
                                           :websecure {:hostPort 443}}
                                   :service {:type "NodePort"
                                             :externalTrafficPolicy "Local"}
                                   :image {:repository repo
                                           :tag tag}
                                   :config {:email (-> cfg :dns-email)}}}))))
        chart (new (.. helm-v3 -Chart) service-name
                   (clj->js {:chart service-name
                             :fetchOpts {:repo "https://caddyserver.github.io/ingress"}
                             :namespace namespace
                             :values helm-values})
                   (clj->js {:provider provider
                             :dependsOn [ns custom-caddy-image]}))]
    chart))