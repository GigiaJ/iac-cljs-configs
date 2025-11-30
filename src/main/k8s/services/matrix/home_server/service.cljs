(ns k8s.services.matrix.home-server.service)

(def config
  {:stack [:vault:prepare [:k8s :pvc :deployment :service :httproute]]
   :image-port    6167
   :app-namespace "matrix"
   :app-name      "tuwunel"
   
   :k8s:pvc-opts
   {"conduwuit-db" {:storageClass "hcloud-volumes"
                    :accessModes ["ReadWriteOnce"]
                    :storage "20Gi"}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:containers
       [{:name 'app-name
         :image '(str repo "/tuwunel:latest")
         :envFrom [{:secretRef {:name '(str app-name "-secrets")}}]
         :volumeMounts [{:name "db" :mountPath "/var/lib/conduwuit"}
                        #_{:name "discord-reg"
                         :mountPath "/etc/conduwuit/discord-registration.yaml"
                         :subPath "registration.yaml"}]}]

       :volumes
       [{:name "db" :persistentVolumeClaim {:claimName "conduwuit-db"}}
        #_{:name "discord-reg" :configMap {:name "discord-bridge-config"}}
        ]}}}}

   :k8s:service-opts
   {:spec {:ports [{:name 'app-name :port 'port :targetPort 'port}]}}

   :k8s:httproute-opts
   {:spec
    {:hostnames ['homeserver]
     :rules [{:matches [{:path {:type "PathPrefix" :value "/_matrix/media"}}]
              :backendRefs [{:name "mmr" :port 8000}]}

             {:matches [{:path {:type "PathPrefix" :value "/_matrix/client/v1/media"}}]
              :backendRefs [{:name "mmr" :port 8000}]}

             {:matches [{:path {:type "PathPrefix" :value "/.well-known/matrix"}}]
              :backendRefs [{:name "matrix-well-known" :port 80}]}

             {:matches [{:path {:type "PathPrefix" :value "/"}}]
              :backendRefs [{:name 'app-name :port 'port}]}]}}})