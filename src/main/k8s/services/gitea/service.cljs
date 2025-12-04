(ns k8s.services.gitea.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:deployment :k8s:service :k8s:httproute :k8s:tcproute]
   :app-namespace "generic"
   :app-name      "gitea"
   :image-port 3000
   :k8s:pvc-opts
   {:metadata {:name "gitea-state"
               :namespace "generic"}
    :spec {:storageClassName "juicefs-sc"
           :accessModes ["ReadWriteMany"]
           :resources {:requests {:storage "1Ti"}}}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:metadata {:annotations {"backup.velero.io/backup-volumes" "gitea-state"}}
      :spec
      {:containers
       [{:name 'app-name
         :image '(str repo "/" app-name ":latest-rootless")
         :command ["/usr/local/bin/gitea"]
         :args ["web"
                "-c" "/var/lib/gitea/custom/conf/app.ini"]
         :env [{:name "TZ" :value "America/Chicago"}]
         :envFrom [{:secretRef {:name "gitea-secrets"}}]
         :ports [{:name "ssh"  :containerPort 2222}]

         :volumeMounts [{:name "gitea-state" :mountPath "/var/lib/gitea"}]}]

       :volumes
       [{:name "gitea-state"
         :persistentVolumeClaim {:claimName "gitea-state"}}]}}}}

   :k8s:service-opts
   {:spec
    {:type "NodePort"
     :selector {:app "gitea"}
     :ports [{:name 'app-name :port 3000 :targetPort 3000}

             {:name "ssh"
              :port 22
              :targetPort 2222
              :nodePort 30022}]}}
   :k8s:httproute-opts {:spec {::hostnames ['host]
                               :rules [{:matches [{:path {:type "PathPrefix"
                                                          :value "/"}}]
                                        :backendRefs [{:name 'app-name
                                                       :port 3000}]}]}}})

