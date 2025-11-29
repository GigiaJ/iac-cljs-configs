(ns k8s.services.foundryvtt.service-2)

(def config
  {:stack [:vault:prepare [:k8s :pvc :deployment :service :httproute]]
   :image-port     30000
   :app-namespace "generic"
   :app-name      "girls-foundry" 
   :k8s:deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]
                                                  :volumes [{:name "data-vol"
                                                             :persistentVolumeClaim {:claimName "girls-vtt-assets"}}]
                                                  :containers [{:name 'app-name :image '(str registry-base "/" registry-namespace "/" "foundry" ":latest")
                                                                :volumeMounts [{:name "data-vol"
                                                                                :mountPath "/root/.local/share"
                                                                                :mountPropagation "HostToContainer"}]}]}}}} 
   :k8s:pvc-opts
   {:metadata {:name "girls-vtt-assets"
               :namespace "generic"}
    :spec {:storageClassName "juicefs-sc"
           :accessModes ["ReadWriteMany"]
           :resources {:requests {:storage "10Gi"}}}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})
