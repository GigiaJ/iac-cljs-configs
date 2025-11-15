;;     - ./personal/matrix/discord/data:/data

(ns k8s.services.matrix.mautrix-discord.service)

(def config
  {:stack [:vault-secrets :docker-image :deployment :service :ingress]
   :image-port     80
   :app-namespace "matrix"
   :app-name      "mautrix-discord"
   :deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name :image '(str repo "/" "discord" ":4927a73ce7411f3970803d35c22f0c8c96dc2d7e-amd64")}]}}}}})