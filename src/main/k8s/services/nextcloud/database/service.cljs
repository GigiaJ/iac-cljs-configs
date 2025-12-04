(ns k8s.services.nextcloud.database.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:deployment :k8s:service]
   :app-namespace "nextcloud"
   :app-name      "nextcloud-db"

   :k8s:pvc-opts
   {:metadata {:name "nextcloud-mariadb-disk"
               :namespace "nextcloud"}
    :spec {:storageClassName "hcloud-volumes"
           :accessModes ["ReadWriteOnce"]
           :resources {:requests {:storage "10Gi"}}}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:metadata
      {:annotations {"backup.velero.io/backup-volumes" "db"}}

      :spec
      {:containers
       [{:name 'app-name
         :image "mariadb:10.6"

         :ports [{:containerPort 3306}]

         :env [{:name "MYSQL_ROOT_PASSWORD" :value 'mariadb-root-password}
               {:name "MYSQL_DATABASE" :value "nextcloud"}
               {:name "MYSQL_USER" :value 'username}
               {:name "MYSQL_PASSWORD" :value 'mariadb-password}]

         :volumeMounts [{:name "db" :mountPath "/var/lib/mysql"}]}]

       :volumes
       [{:name "db" :persistentVolumeClaim {:claimName "nextcloud-mariadb-disk"}}]}}}}

   :k8s:service-opts
   {:spec {:selector {:app 'app-name}
           :ports [{:name 'app-name :port 3306 :targetPort 3306}]}}})