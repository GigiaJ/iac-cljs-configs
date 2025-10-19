(ns k8s.add-ons.proxy
  (:require [clojure.string :as str]))

(def wasabi-proxy-caddyfile
  (str/join "\n"
            [":80 {"
             ""
             "  reverse_proxy https://s3.wasabisys.com {" 
             "    flush_interval -1"
             "    redirect_mode off"
             "    transport http {"
             "      versions 1.1"
             "    }"
             "    header_up -X-Forwarded-For"
             "    header_up -X-Forwarded-Proto"
             "    header_up -X-Forwarded-Host"
             "    header_up -Transfer-Encoding"
             "    header_up Content-Type {http.request.header.Content-Type}"
             "  }"
             "}"]))

(def config
  {:stack [:config-map :deployment :service]

   :app-namespace "wasabi-proxy"
   :app-name      "wasabi-proxy"
   :image-port 80
   :image "docker.io/library/caddy:2"
   :vault-load-yaml false

   :config-map-opts {:data {:Caddyfile wasabi-proxy-caddyfile}}

   :deployment-opts
   {:spec
    {:template
     {:spec
      {:containers
       [{:name "wasabi-proxy"
         :image "docker.io/library/caddy:2"
         :ports [{:containerPort 80}]
         :volumeMounts
         [{:name "caddyfile-config"
           :mountPath "/etc/caddy/Caddyfile"
           :subPath "Caddyfile"}
          {:name "caddy-data"
           :mountPath "/data/caddy"}]}]

       :volumes
       [{:name "caddyfile-config"
         :configMap {:name "wasabi-proxy"}}
        {:name "caddy-data"
         :emptyDir {}}]
       :nodeSelector {"node-role.kubernetes.io/master" "true"}}}}}

   :service-opts
   {:spec
    {:ports
     [{:port 80
       :targetPort 80}]}}})

