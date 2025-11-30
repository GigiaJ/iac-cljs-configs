(ns k8s.services.matrix.mmr.database.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:deployment :k8s:service]
   :app-namespace "matrix"
   :app-name      "mmr-db"

   :k8s:pvc-opts
   {"mmr-pg-data" {:storageClass "hcloud-volumes"
                   :accessModes ["ReadWriteOnce"]
                   :storage "10Gi"}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:containers
       [{:name 'app-name
         :image "postgres:14-alpine"
         :env [{:name "POSTGRES_USER" :value "mmr"}
               {:name "POSTGRES_PASSWORD" :value "mmr_password"}
               {:name "POSTGRES_DB" :value "media_repo"}]
         :volumeMounts [{:name "db" :mountPath "/var/lib/postgresql/data"}]}]

       :volumes
       [{:name "db" :persistentVolumeClaim {:claimName "mmr-pg-data"}}]}}}}

   :k8s:service-opts
   {:spec {:selector {:app 'app-name}
           :ports [{:port 5432 :targetPort 5432}]}}})