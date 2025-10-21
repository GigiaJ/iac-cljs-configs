(ns k8s.services.mesite.service)

(def config
  {:stack [:vault-secrets :deployment :service :ingress]
   :image-port     80
   :app-namespace "generic"
   :app-name      "mesite"
   :deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]
                                              :containers [{:name 'app-name :image '(str repo "/" app-name ":latest")}]}}}}})