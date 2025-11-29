(ns k8s.services.foundryvtt.service)

;; Need to automate license unlock
(def config
  {:stack [:vault:prepare :harbor:robot-account :docker:image [:k8s :pvc :deployment :service :httproute]]
   :image-port     30000
   :app-namespace "generic"
   :app-name      "foundry"
   :docker:image-opts {:is-local true
                       :buildArgs {:FOUNDRY_USERNAME 'FOUNDRY_USERNAME
                                   :FOUNDRY_PASSWORD 'FOUNDRY_PASSWORD} 
                       :registry {:server '(str registry-base "/" registry-namespace)
                                  :username '(-> :harbor:robot-account .-name)
                                  :password '(-> :harbor:robot-account .-secret)}
                       :tags ['(str registry-base "/" registry-namespace "/" app-name)]
                       :push true}
   :k8s:deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]
                                                  :volumes [{:name "data-vol"
                                                            :persistentVolumeClaim {:claimName "vtt-assets"}}]
                                                  :containers [{:name 'app-name :image '(str registry-base "/" registry-namespace "/" app-name ":latest")
                                                                :volumeMounts [{:name "data-vol"
                                                                                :mountPath "/root/.local/share"
                                                                                :mountPropagation "HostToContainer"}]
                                                                }]}}}}
   :harbor:robot-account-opts {:name 'app-name
                               :permissions [{:kind "project"
                                              :namespace 'registry-namespace
                                              :access [{:action "pull" :resource "repository"}
                                                       {:action "push" :resource "repository"}
                                                       {:action "list" :resource "repository"}]}]}
   :k8s:pvc-opts
   {:metadata {:name "vtt-assets"
               :namespace "generic"}
    :spec {:storageClassName "juicefs-sc"
           :accessModes ["ReadWriteMany"] 
           :resources {:requests {:storage "10Gi"}}}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})
