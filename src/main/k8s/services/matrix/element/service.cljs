(ns k8s.services.matrix.element.service)

(def config
  {:stack [:vault:prepare [:k8s :config-map :deployment :service :httproute]]
   :image-port    80
   :app-namespace "matrix"
   :app-name      "element-web"
   :k8s:config-map-opts {:data {"config.json"
                                '(stringify
                                  {:default_server_name homeserver
                                   :default_server_config
                                   {:m.homeserver
                                    {:base_url (str "https://" homeserver)}
                                    :m.identity_server
                                    {:base_url identity-server}}

                                   :brand brand-name

                                   :integrations_ui_url "https://scalar.vector.im/"
                                   :integrations_rest_url "https://scalar.vector.im/api"
                                   :integrations_widgets_urls
                                   ["https://scalar.vector.im/_matrix/integrations/v1"
                                    "https://scalar.vector.im/api"
                                    "https://scalar-staging.vector.im/_matrix/integrations/v1"
                                    "https://scalar-staging.vector.im/api"
                                    "https://scalar-staging.riot.im/scalar/api"]

                                   :bug_report_endpoint_url "https://element.io/bugreports/submit"
                                   :uisi_autorageshake_app "element-auto-uisi"
                                   :show_labs_settings true
                                   :room_directory
                                   {:servers [homeserver]}
                                   :enable_presence_by_hs_url
                                   {"https://matrix.org" false
                                    "https://matrix-client.matrix.org" false}
                                   :terms_and_conditions_links
                                   [{:url (str "https://" homeserver "/privacy")
                                     :text "Privacy Policy"}
                                    {:url (str "https://" homeserver "/cookie-policy")
                                     :text "Cookie Policy"}]
                                   :sentry
                                   {:dsn "https://029a0eb289f942508ae0fb17935bd8c5@sentry.matrix.org/6"
                                    :environment "develop"}
                                   :posthog
                                   {:project_api_key "phc_Jzsm6DTm6V2705zeU5dcNvQDlonOR68XvX2sh1sEOHO"
                                    :api_host (str "https://posthog." homeserver)}
                                   :privacy_policy_url (str "https://" homeserver "/cookie-policy")
                                   :features
                                   {:threadsActivityCentre true
                                    :feature_video_rooms true
                                    :feature_group_calls true
                                    :feature_element_call_video_rooms true}
                                   :setting_defaults
                                   {:RustCrypto.staged_rollout_percent 100
                                    :Registration.mobileRegistrationHelper true}
                                   :element_call
                                   {:url (str "https://livekit." homeserver)}
                                   :map_style_url "https://api.maptiler.com/maps/streets/style.json?key=fU3vlMsMn4Jb6dnEIFsx"})}}
   :k8s:deployment-opts {:spec
                         {:template
                          {:spec
                           {:volumes [{:name "config-vol"
                                       :configMap {:name 'app-name}}]
                            :containers [{:name 'app-name :image '(str repo "/" app-name ":latest")
                                          :env [{:name "ELEMENT_WEB_PORT" :value "80"}]
                                          :volumeMounts [{:name "config-vol"
                                                          :mountPath "/app/config.json"
                                                          :subPath "config.json"}]}]}}}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})