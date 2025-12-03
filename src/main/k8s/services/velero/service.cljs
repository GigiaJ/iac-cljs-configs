(ns k8s.services.velero.service)

(def config
  {:stack [:vault:prepare :k8s:secret :k8s:chart]
   :app-namespace "velero"
   :app-name      "velero"

   :k8s:secret-opts
   {:metadata {:name "velero-s3-creds"}
    :stringData {"cloud" '(str "[default]\n"
                               "aws_access_key_id = " s3-access-key "\n"
                               "aws_secret_access_key = " s3-secret-key)}}

   :k8s:chart-opts
   {:repositoryOpts {:repo 'repo}
    :chart "velero"
    :version "5.1.0"
    
    :values
    {:deployNodeAgent true

     :configuration
     {:backupStorageLocation
      [{:name "default"
        :provider "aws"
        :bucket 's3-bucket-name
        :config {:region 's3-region
                 :s3ForcePathStyle true
                 :s3Url 's3-url}}]

      :volumeSnapshotLocation
      [{:name "default"
        :provider "aws"
        :config {:region 's3-region}}]}


     :credentials {:useSecret true
                   :existingSecret "velero-s3-creds"}

     :initContainers
     [{:name "velero-plugin-for-aws"
       :image "velero/velero-plugin-for-aws:v1.8.0"
       :volumeMounts [{:mountPath "/target" :name "plugins"}]}]

     :defaultVolumesToFsBackup true

     :nodeAgent {:resources {:requests {:cpu "50m" :memory "64Mi"}
                             :limits   {:cpu "1000m" :memory "1Gi"}}}

     :schedules
     {:daily-backup
      {:disabled false
       :schedule "0 4 * * *"
       :template {:ttl "720h"
                  :includedNamespaces ["matrix" "generic" "home" "nextcloud"]}}}}}})