(ns k8s.services.matrix.element-call.livekit-server.service)

(defn generate-all-ports [tcp-port start-udp end-udp]
  (concat
   [{:name "http"
     :port tcp-port
     :targetPort tcp-port
     :containerPort tcp-port
     :protocol "TCP"}]

   (map (fn [p]
          {:name (str "udp-" p)
           :port p
           :targetPort p
           :nodePort p
           :containerPort p
           :protocol "UDP"})
        (range start-udp (inc end-udp)))))

(def all-ports (generate-all-ports 7880 31000 31100))


(def config
  {:stack [:vault:prepare [:k8s :config-map :deployment :service :httproute]]
   :image-port    nil
   :app-namespace "matrix"
   :app-name      "livekit-server"

   :k8s:config-map-opts
   {:metadata {:name "livekit-config"}
    :data {"livekit.yaml"
           '(stringify
             {:port 7880
              :bind_addresses ["0.0.0.0"]
              :rtc {:tcp_port 7881
                    :port_range_start 31000
                    :port_range_end 31100
                    :use_external_ip true} ;; Required for Hetzner Public IP discovery

              :logging {:level "debug"}
              :turn {:enabled false
                     :udp_port 443
                     :tls_port 5349}

              :keys {:devkey dev-key}})}}
   
   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:volumes [{:name "config-vol" :configMap {:name "livekit-config"}}]
       :containers [{:name 'app-name
                     :image '(str repo "/" app-name ":latest")
                     :command ["/livekit-server"]
                     :args ["--config" "/etc/livekit.yaml"]
                     :ports (map #(select-keys % [:name :containerPort :protocol])
                                 all-ports)
                     :volumeMounts [{:name "config-vol"
                                     :mountPath "/etc/livekit.yaml"
                                     :subPath "livekit.yaml"}]}]}}}}

   :k8s:service-opts
   {:spec {:type "NodePort"
           :selector {:app 'app-name}
           :ports (map #(select-keys % [:name :port :targetPort :nodePort :protocol])
                       all-ports)}}

   :k8s:httproute-opts
   {:spec
    {:hostnames ['host]
     :rules [{:matches [{:path {:type "PathPrefix" :value "/livekit/sfu"}}]
              :filters [{:type "URLRewrite"
                         :urlRewrite {:path {:type "ReplacePrefixMatch"
                                             :replacePrefixMatch "/"}}}]

              :backendRefs [{:name 'app-name :port 7880}]}]}}})