(ns k8s.services.matrix.home-server.well-known.service)

(def config
  {:stack [:vault:prepare :k8s:config-map :k8s:deployment :k8s:service]
   :app-namespace "matrix"
   :app-name      "matrix-well-known"

   :k8s:config-map-opts
   {:metadata {:name "well-known-json"}
    :data {"server" "{\"m.server\": \"hampter.quest:443\"}"
           "client" '(stringify
                      {:m.homeserver {:base_url (str "https://" homeserver)}
                       :org.matrix.msc4143.rtc_foci [{:type "livekit"
                                                      :livekit_service_url livekit-url}]})}}
   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:containers
       [{:name "nginx"
         :image "nginx:alpine"
         :volumeMounts [{:name "config" :mountPath "/usr/share/nginx/html/.well-known/matrix"}]}]
       :volumes [{:name "config" :configMap {:name "well-known-json"}}]}}}}

   :k8s:service-opts
   {:spec {:ports [{:port 80 :targetPort 80}]}}})