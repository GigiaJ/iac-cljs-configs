(ns k8s.services.gitea.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:deployment :k8s:service :k8s:httproute]
   :image-port    3000
   :app-namespace "generic"
   :app-name      "gitea"

   :k8s:pvc-opts
   {"gitea-data"   {:storageClass "juicefs-sc" :accessModes ["ReadWriteMany"] :storage "1Ti"}
    "gitea-config" {:storageClass "juicefs-sc" :accessModes ["ReadWriteMany"] :storage "1Gi"}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:initContainers
       [{:name "init-permissions"
         :image "busybox:latest"
         :command ["sh" "-c" "chown -R 1000:1000 /var/lib/gitea && chown -R 1000:1000 /etc/gitea"]
         :volumeMounts [{:name "gitea-data" :mountPath "/var/lib/gitea"}
                        {:name "gitea-config" :mountPath "/etc/gitea"}]
         :securityContext {:runAsUser 0 :runAsGroup 0}}]

       :containers
       [{:name 'app-name
         :image '(str repo "/" app-name ":latest-rootless")

         :env [{:name "TZ" :value "America/Chicago"}]
         :envFrom [{:secretRef {:name "gitea-secrets"}}]
         :ports [{:name "http" :containerPort 3000}
                 {:name "ssh"  :containerPort 2222}]

         :volumeMounts [{:name "gitea-data" :mountPath "/var/lib/gitea"}
                        {:name "gitea-config" :mountPath "/etc/gitea"}]}]

       :volumes
       [{:name "gitea-data" :persistentVolumeClaim {:claimName "gitea-data"}}
        {:name "gitea-config" :persistentVolumeClaim {:claimName "gitea-config"}}]}}}}

   :k8s:service-opts
   {:spec
    {:type "NodePort"
     :selector {:app "gitea"}
     :ports [
             {:name 'app-name :port 3000 :targetPort 3000}

             {:name "ssh"
              :port 22         
              :targetPort 2222 
              :nodePort 30022 
              }]}}

   :k8s:httproute-opts {:spec {::hostnames ['host]}}})