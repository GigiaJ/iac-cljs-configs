(ns k8s.services.mesite.service)

(defn test [env]
  (js/console.log env)
  (.apply (:test env) #(js/console.log %)))

(def config
  {:stack [:vault:prepare
           :harbor:robot-account
           :docker:image
           [:k8s :namespace :deployment :service :ingress :httproute]] 
   :app-name "mesite"
   :namespace "generic"
   :docker:image-opts {:context {:location "https://codeberg.org/Gigia/mesite.git"}
                       :imageName '(str registry-base "/" registry-namespace "/" app-name ":latest")
                       :registry {:server '(str registry-base "/" registry-namespace)
                                  :username '(-> :harbor:robot-account .-name)
                                  :password '(-> :harbor:robot-account .-secret)}
                       :tags ['(str registry-base "/" registry-namespace "/" app-name)]
                       :push true}

   :harbor:robot-account-opts {:name 'app-name
                               :permissions [{:kind "project"
                                              :namespace 'registry-namespace
                                              :access [{:action "pull" :resource "repository"}
                                                       {:action "push" :resource "repository"}
                                                       {:action "list" :resource "repository"}]}]} 
   
   :k8s:deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]
                                                  :containers [{:name 'app-name
                                                                :image '(str registry-base "/" registry-namespace "/" app-name ":latest")
                                                                :ports [{:containerPort 80}]}]}}}} 
   :k8s:httproute-opts {:spec {::hostnames ['host]}}
   })