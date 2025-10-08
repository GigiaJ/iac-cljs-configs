(ns k8s.services.foundryvtt.service
  (:require
   [configs :refer [cfg]]))

(def config
  {:stack [:vault-secrets :deployment :service :ingress]
   :image-port     80
   :app-namespace "generic"
   :app-name      "foundry"
   :image    (str (-> cfg :docker-repo) "/mesite:latest")})


