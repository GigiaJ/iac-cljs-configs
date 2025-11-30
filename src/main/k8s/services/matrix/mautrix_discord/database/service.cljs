(ns k8s.services.matrix.mautrix-discord.database.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:config-map :k8s:deployment :k8s:service]
   :app-namespace "matrix"
   :app-name      "mautrix-discord"
   :image-port    29334

   :k8s:config-map-opts
   {:metadata {:name "discord-bridge-config"}
    :data {"config.yaml"       "YAML-HERE" ;;TODO
           "registration.yaml" "YAML-HERE"}}

   :k8s:pvc-opts
   {"discord-bridge-data" {:storageClass "juicefs-sc"
                           :accessModes ["ReadWriteMany"]
                           :storage "1Gi"}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:containers
       [{:name 'app-name
         :image "dock.mau.dev/mautrix/discord:latest"
         :args ["/usr/bin/mautrix-discord" "-c" "/data/config.yaml" "-r" "/data/registration.yaml"]

         :volumeMounts [{:name "data"   :mountPath "/data"}
                        {:name "config" :mountPath "/data/config.yaml" :subPath "config.yaml"}
                        {:name "config" :mountPath "/data/registration.yaml" :subPath "registration.yaml"}]}]

       :volumes
       [{:name "data" :persistentVolumeClaim {:claimName "discord-bridge-data"}}
        {:name "config" :configMap {:name "discord-bridge-config"}}]}}}}

   :k8s:service-opts
   {:spec {:selector {:app 'app-name}
           :ports [{:port 29334 :targetPort 29334}]}}})