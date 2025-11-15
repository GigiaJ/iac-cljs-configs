(ns k8s.services.matrix.livekit-jwt.service)

(def config
  {:stack [:vault-secrets :docker-image :deployment :service :ingress]
   :image-port     80
   :app-namespace "matrix"
   :app-name      "livekit-jwt"
   :deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name :image '(str repo "/" lk-jwt-service ":0.2.3")}]}}}}})