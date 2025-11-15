;;    volumes:
;;      - ./personal/matrix/element-config.json:/app/config.json
;;    environment:
;;      ELEMENT_WEB_PORT: 3030

(ns k8s.services.matrix.element.service)

(def config
  {:stack [:vault-secrets :docker-image :deployment :service :ingress]
   :image-port     80
   :app-namespace "matrix"
   :app-name      "element"
   :deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]
                                              :containers [{:name 'app-name :image '(str repo "/" app-name ":latest")}]}}}}})