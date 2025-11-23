(ns k8s.services.foundryvtt.service)

(def config
  {:stack [:vault:prepare :harbor:robot-account :docker:image [:k8s :deployment :service :httproute]]
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
                                                  :containers [{:name 'app-name :image '(str registry-base "/" registry-namespace "/" app-name ":latest")}]}}}}
   :harbor:robot-account-opts {:name 'app-name
                               :permissions [{:kind "project"
                                              :namespace 'registry-namespace
                                              :access [{:action "pull" :resource "repository"}
                                                       {:action "push" :resource "repository"}
                                                       {:action "list" :resource "repository"}]}]}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})
