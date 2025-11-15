;;  homeserver:
;;    volumes:
;;        - db:/var/lib/conduwuit
 
 (def config
  {:stack [:vault-secrets :docker-image :deployment :service :ingress]
   :image-port     80
   :app-namespace "matrix"
   :app-name      "tuwunel"
   :deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name :image '(str repo "/" 'app-name ":latest")}]}}}}})