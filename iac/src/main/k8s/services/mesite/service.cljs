(ns k8s.services.mesite.service)

(def config
  {:stack [:vault-secrets:prepare
           :harbor:robot-account
           :docker:image
           [-> :k8s :namespace :deployment :service :ingress]]
   :app-name "mesite"
   :docker:image-opts {:context {:location "https://codeberg.org/Gigia/mesite.git"}
                       :registry {:server 'repo
                                  :username '(-> :harbor:robot-account .-name)
                                  :password '(-> :harbor:robot-account .-secret)}
                       :tags ['(str registry-base "/" registry-namespace "/" app-name)]
                       :push true}

   :harbor:robot-account-opts {:permissions [{:kind "project"
                                              :namespace 'registry-namespace
                                              :access [{:action "push" :resource "repository"}
                                                       {:action "list" :resource "repository"}]}]}
   :k8s:deployment-opts {:spec {:template {:spec {:imagePullSecrets [{:name "harbor-creds-secrets"}]}}}}})