(ns k8s.services.matrix.database.service)
;;     env_file:
;;      - .env
;;    volumes:
 ;;     - ${PWD}/db-data/:/var/lib/postgresql/data/


(def config
  {:stack [:deployment :service :ingress]
   :image-port     80
   :app-namespace "matrix"
   :app-name      "postgres"
   :deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name :image '(str repo "/" 'app-name ":latest")}]}}}}})