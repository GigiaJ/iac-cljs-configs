(ns k8s.services.matrix.mautrix-discord.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:config-map :k8s:deployment :k8s:service]
   :app-namespace "matrix"
   :app-name      "mautrix-discord"

   :k8s:config-map-opts
   {:metadata {:name "discord-bridge-config"}
    :data {"config.yaml"       '(stringify
                                 {:homeserver {:address (str "https://" homeserver)
                                               :domain homeserver}
                                  :appservice {:port port
                                               :address (str "http://mautrix-discord:" port)
                                               :hostname "0.0.0.0"
                                               :database {:type "postgres"
                                                          :uri db-login-url
                                                          :max_open_conns 20
                                                          :max_idle_cons 2}
                                               :id "discord"
                                               :as_token as-token
                                               :hs_token hs-token
                                               :ephemeral_events true
                                               :bot {:username "discordbot"
                                                     :displayname "Discord bridge bot"}}
                                  :bridge {:permissions (parse permissions)
                                           ;;:login_shared_secret_map (parse login-shared-secret-map)
                                           ;;:double_puppet_server_map (parse double-puppet-server-map)
                                           :use_discord_cdn_upload true
                                           :command_prefix "!discord"
                                           :encryption {:allow false
                                                        :default false}}})
           "registration.yaml" '(stringify {:id "discord"
                                            :url (str "http://mautrix-discord:" port)
                                            :as_token as-token
                                            :hs_token hs-token
                                            :sender_localpart sender-localpart
                                            :rate_limited false
                                            :namespaces {:users [{:regex user1-regex
                                                                  :exclusive true}
                                                                 {:regex user2-regex
                                                                  :exclusive true}]}
                                            :de.sorunome.msc2409.push_ephemeral true
                                            :push_ephemeral true})}}

   
   :k8s:pvc-opts
   {:metadata {:name "discord-bridge-data"
               :namespace "matrix"}
    :spec {:storageClassName "juicefs-sc"
           :accessModes ["ReadWriteMany"]
           :resources {:requests {:storage "1Gi"}}}}
   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:initContainers
       [{:name "config-loader"
         :image "busybox:latest"
         :command ["sh" "-c" "cp -f /config_source/* /data/"]
         :volumeMounts [{:name "data" :mountPath "/data"}
                        {:name "config" :mountPath "/config_source"}]}]
       :containers
       [{:name 'app-name
         :image '(str repo "/discord:latest")
         :args ["/usr/bin/mautrix-discord" "-c" "/data/config.yaml" "-r" "/data/registration.yaml"]
         :ports [{:containerPort 'port}]
         :volumeMounts [{:name "data"   :mountPath "/data"}
                        #_{:name "config" :mountPath "/data/config.yaml" :subPath "config.yaml"}
                        #_{:name "config" :mountPath "/data/registration.yaml" :subPath "registration.yaml"}]}]

       :volumes
       [{:name "data" :persistentVolumeClaim {:claimName "discord-bridge-data"}}
        {:name "config" :configMap {:name "discord-bridge-config"}}]}}}}

   :k8s:service-opts
   {:spec {:selector {:app 'app-name}
           :ports [{:name 'app-name :port 'port :targetPort 'port}]}}})