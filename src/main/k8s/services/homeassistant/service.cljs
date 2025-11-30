(ns k8s.services.homeassistant.service)

(def config
  {:stack [:vault:prepare :k8s:pvc :k8s:deployment :k8s:service :k8s:httproute]
   :image-port    8123
   :app-namespace "home"
   :app-name      "homeassistant"

   :k8s:pvc-opts
   {"ha-config" {:storageClass "hcloud-volumes"
                 :accessModes ["ReadWriteOnce"]
                 :storage "10Gi"}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:containers
       [{:name 'app-name
         :image '(str repo  "/home-assistant:stable")
         :env [{:name "TZ" :value "America/Chicago"}]
         :volumeMounts [{:name "config" :mountPath "/config"}]}]
       :volumes
       [{:name "config" :persistentVolumeClaim {:claimName "ha-config"}}]}}}}

   :k8s:service-opts
   {:spec {:selector {:app 'app-name}
           :ports [{:name 'app-name :port 8123 :targetPort 8123}]}}

   :k8s:httproute-opts
   {:spec
    {:hostnames ['host]
     :rules [{:matches [{:path {:type "PathPrefix" :value "/"}}]
              :backendRefs [{:name 'app-name :port 8123}]}]}}})