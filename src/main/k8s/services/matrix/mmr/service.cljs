
(ns k8s.services.matrix.mmr.service)

(def config
  {:stack [:vault:prepare [:k8s :config-map :deployment :service :httproute]]
   :image-port    80
   :app-namespace "matrix"
   :app-name      "matrix-media-repo"
   :k8s:config-map-opts
   {:metadata {:name "mmr-config"}
    :data {"media-repo.yaml"
           '(stringify
             {:repo {:port port
                     :bindAddress "0.0.0.0"
                     :logLevel "debug"}
              :database {:postgres db-login-url}
              :homeservers [{:name homeserver
                             :csApi (str "https://" homeserver)}]
              :accessTokens {:appservices [{:id "discord"
                                            :asToken discord-app-service-token
                                            :senderUserId discord-send-user-id
                                            :userNamespaces {:regex user-namespace-regex}}]}
              :admins [admin]
              :datastores [{:type "s3"
                            :id s3-id
                            :forKinds ["all"]
                            :opts {:tempPath "/tmp/media-repo"
                                   :endpoint s3-endpoint
                                   :accessKeyId s3-access-key
                                   :accessSecret s3-secret-key
                                   :ssl true
                                   :bucketName s3-bucket-name
                                   :region s3-region}}]
              :rateLimit {:enabled false}})}}
   :k8s:deployment-opts {:spec {:template {:spec {:containers [{:args ["-config" "/etc/media-repo.yaml"]
                                                                :name 'app-name
                                                                :image '(str repo "/" app-name ":v1.3.8")
                                                                :volumeMounts [{:name "config-vol"
                                                                                :mountPath "/mmr"
                                                                                :subPath "media-repo.yaml"}
                                                                               {:name "temp-vol"
                                                                                :mountPath "/tmp/media-repo"}]}]
                                               :volumes [{:name "config-vol" :configMap {:name "mmr-config"}}
                                                            {:name "temp-vol"
                                                             :emptyDir {}}]}}}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})


   