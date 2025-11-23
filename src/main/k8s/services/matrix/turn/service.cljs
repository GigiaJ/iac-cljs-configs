(ns k8s.services.matrix.turn.service)

;;     - ./personal/matrix/coturn.conf:/etc/coturn/turnserver.conf

(def config
  {:stack [:vault-secrets :docker-image :deployment :service :ingress]
   :image-port     80
   :app-namespace "matrix"
   :app-name      "coturn"
   :image-opts {:build {:args {:FOUNDRY_USERNAME 'FOUNDRY_USERNAME
                               :FOUNDRY_PASSWORD 'FOUNDRY_PASSWORD}}
                :imageName '(str repo "/" app-name ":latest")}
   :deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]
                                              :containers [{:name 'app-name :image '(str repo "/" app-name ":latest")}]}}}}})