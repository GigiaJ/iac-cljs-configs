(ns k8s.services.matrix.mautrix-discord.database.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:deployment :k8s:service]
   :app-namespace "matrix"
   :app-name      "mautrix-discord-db"

   :k8s:pvc-opts
   {:metadata {:name "mautrix-discord-pg-data"
               :namespace "matrix"}
    :spec {:storageClassName "hcloud-volumes"
           :accessModes ["ReadWriteOnce"]
           :resources {:requests {:storage "10Gi"}}}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:metadata
      {:annotations
       {"backup.velero.io/backup-volumes" "db"}}
      :spec 
      {:containers
       [{:name 'app-name
         :image "postgres:14-alpine"
         :ports [{:containerPort 5432}]
         :env [{:name "PGDATA" :value "/var/lib/postgresql/data/pgdata"}
               {:name "POSTGRES_USER" :value 'username}
               {:name "POSTGRES_PASSWORD" :value 'password}
               {:name "POSTGRES_DB" :value 'db-name}]
         :volumeMounts [{:name "db" :mountPath "/var/lib/postgresql/data"}]}]

       :volumes
       [{:name "db" :persistentVolumeClaim {:claimName "mautrix-discord-pg-data"}}]}}}}

   :k8s:service-opts
   {:spec {:selector {:app 'app-name}
           :ports [{:name 'app-name :port 5432 :targetPort 5432}]}}})