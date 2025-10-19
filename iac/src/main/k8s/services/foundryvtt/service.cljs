(ns k8s.services.foundryvtt.service)

(def config
  {:stack [:docker-image :vault-secrets :deployment :service :ingress]
   :image-port     80
   :app-namespace "generic"
   :app-name      "foundry"
   :image-opts {:imageName '(str repo "/" app-name ":latest")}
   :deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name :image '(str repo "/" app-name ":latest")}]}}}}})
