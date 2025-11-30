(ns k8s.services.matrix.turn.service)

(defn generate-all-ports [start-relay end-relay]
  (concat
   [{:name "signaling-udp" :port 3478 :targetPort 3478 :nodePort 30478 :containerPort 3478 :protocol "UDP"}
    {:name "signaling-tcp" :port 3478 :targetPort 3478 :nodePort 30478 :containerPort 3478 :protocol "TCP"}

    {:name "tls-udp" :port 5349 :targetPort 5349 :nodePort 30549 :containerPort 5349 :protocol "UDP"}
    {:name "tls-tcp" :port 5349 :targetPort 5349 :nodePort 30549 :containerPort 5349 :protocol "TCP"}]

   (map (fn [p]
          {:name (str "relay-" p)
           :port p
           :targetPort p
           :nodePort p
           :containerPort p
           :protocol "UDP"})
        (range start-relay (inc end-relay)))))

(def all-ports (generate-all-ports 32000 32050))

(def config
  {:stack [:vault:prepare [:k8s :config-map :deployment :service]]
   :image-port    nil
   :app-namespace "matrix"
   :app-name      "coturn"

   :k8s:config-map-opts
   {:metadata {:name "coturn-config"}
    :data {"turnserver.conf"
           '(str
             "listening-port=3478\n"
             "tls-listening-port=5349\n"
             "min-port=32000\n"
             "max-port=32050\n"

             (str "external-ip=" public-ip "\n")

             (str "realm" homeserver "\n")
             (str "server-name=" host "\n")
             "log-file=stdout\n"

             "use-auth-secret\n"
             (str "static-auth-secret=" secret-auth "\n")
             "fingerprint\n"
             "lt-cred-mech\n")}}

   :k8s:deployment-opts
   {:spec
    {:template
     {:spec
      {:volumes [{:name "config" :configMap {:name "coturn-config"}}]
       :containers [{:name 'app-name
                     :image "coturn/coturn:latest"

                     :ports (map #(select-keys % [:name :containerPort :protocol])
                                 all-ports)

                     :volumeMounts [{:name "config"
                                     :mountPath "/etc/coturn/turnserver.conf"
                                     :subPath "turnserver.conf"}]}]}}}}

   :k8s:service-opts
   {:spec {:type "NodePort"
           :selector {:app 'app-name}
           :ports (map #(select-keys % [:name :port :targetPort :nodePort :protocol])
                       all-ports)}}})