(ns k8s.services.foundryvtt.service)

(def config
  {:stack [:vault-secrets :docker-image :deployment :service :ingress]
   :image-port     80
   :app-namespace "generic"
   :app-name      "foundry"
   :image-opts {:build {:args {:FOUNDRY_USERNAME 'FOUNDRY_USERNAME
                               :FOUNDRY_PASSWORD 'FOUNDRY_PASSWORD}}
                :imageName '(str repo "/" app-name ":latest")}
   :deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]
                                              :containers [{:name 'app-name :image '(str repo "/" app-name ":latest")}]}}}}})
