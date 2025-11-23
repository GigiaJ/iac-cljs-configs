(ns k8s.services.matrix.element-call.service)

;;    volumes:
;;      - ./personal/matrix/elementcall/config.json:/app/config.json

(def config
  {:stack [:vault-secrets :deployment :service :ingress]
   :image-port    80
   :app-namespace "matrix"
   :app-name      "element-call"
   :deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name :image '(str repo "/" app-name ":sha-1702b15")
                                                            :volumeMounts [{:name "data" :mountPath "/data"}]}]
                                              :initContainers [{:name "init-permissions"
                                                                :image "busybox:latest"
                                                                :command ["sh" "-c" "chown -R 1000:1000 /data"]
                                                                :volumeMounts [{:name "data" :mountPath "/data"}]
                                                                :securityContext {:runAsUser 0 :runAsGroup 0}}]
                                              :volumes [{:name "data" :hostPath {:path "/opt/mmr/data" :type "DirectoryOrCreate"}}]}}}}})