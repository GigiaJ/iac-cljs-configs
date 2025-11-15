

;;  livekit:
;;    command: --config /etc/livekit.yaml
;;      - ./personal/matrix/elementcall/livekit.yaml:/etc/livekit.yaml
  ;;  ports:
   ;;   - 50100-50200:50100-50200/udp

(ns k8s.services.matrix.livekit-server.service)

(def config
  {:stack [:vault-secrets :docker-image :deployment :service :ingress]
   :image-port     80
   :app-namespace "matrix"
   :app-name      "livekit-server"
   :deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name :image '(str repo "/" 'app-name ":latest")}]}}}}})