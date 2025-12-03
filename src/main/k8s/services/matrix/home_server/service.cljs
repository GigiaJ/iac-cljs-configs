(ns k8s.services.matrix.home-server.service)

(def config
  {:stack [:vault:prepare [:k8s :pvc :deployment :service :httproute]]
   :app-namespace "matrix"
   :app-name      "tuwunel"
   
   
   :k8s:pvc-opts
   {:metadata {:name "conduwuit-db"
               :namespace "matrix"}
    :spec {:storageClassName "hcloud-volumes"
           :accessModes ["ReadWriteOnce"]
           :resources {:requests {:storage "50Gi"}}}}

   :k8s:deployment-opts
   {:spec
    {:strategy {:type "Recreate"}
     :template
     {:metadata {:annotations {"backup.velero.io/backup-volumes" "db"}}
      :spec
      {:containers
       [{:name 'app-name
         :image '(str repo "/tuwunel:latest")
         :envFrom [{:secretRef {:name '(str app-name "-secrets")}}]
         :ports [{:containerPort 'port}]
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
    {:hostnames ['host]
     :rules [{:matches [{:path {:type "PathPrefix" :value "/_matrix/media"}}]
              :backendRefs [{:name "matrix-media-repo" :port 80}]}

             {:matches [{:path {:type "PathPrefix" :value "/_matrix/client/v1/media"}}]
              :backendRefs [{:name "matrix-media-repo" :port 80}]}

             {:matches [{:path {:type "PathPrefix" :value "/.well-known/matrix"}}]
              :backendRefs [{:name "matrix-well-known" :port 80}]}

             {:matches [{:path {:type "PathPrefix" :value "/"}}]
              :backendRefs [{:name 'app-name :port 'port}]}]}}})