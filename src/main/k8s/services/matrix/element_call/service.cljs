(ns k8s.services.matrix.element-call.service)

(def config
  {:stack [:vault:prepare [:k8s :config-map :deployment :service :httproute]]
   :image-port    80
   :app-namespace "matrix"
   :app-name      "element-call"
   :k8s:config-map-opts {:data {"config.json"
                                '(stringify
                                  {:default_server_config
                                   {:m.homeserver
                                    {:base_url (str "https://" homeserver)
                                     :server_name homeserver}}
                                   :features
                                   {:feature_use_device_session_member_events true}
                                   :ssla "https://static.element.io/legal/element-software-and-services-license-agreement-uk-1.pdf"})}}
   :k8s:deployment-opts {:spec
                         {:template
                          {:spec
                           {:volumes [{:name "config-vol"
                                       :configMap {:name 'app-name}}]
                            :containers [{:name 'app-name :image '(str repo "/" app-name ":latest")
                                          :volumeMounts [{:name "config-vol"
                                                          :mountPath "/app/config.json"
                                                          :subPath "config.json"}]}]}}}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})