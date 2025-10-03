(ns k8s.services.mesite.service
  (:require
   [utils.k8s :as k8s-utils]
   [configs :refer [cfg]]))

(defn deploy [provider vault-provider]
  (k8s-utils/deploy-stack
   :namespace :vault-secrets :deployment :service :ingress
   {:provider provider
    :vault-provider vault-provider
    :app-namespace "generic"
    :app-name "mesite"
    :image-port 80
    :image (str (-> cfg :docker-repo) "/mesite:latest")}))