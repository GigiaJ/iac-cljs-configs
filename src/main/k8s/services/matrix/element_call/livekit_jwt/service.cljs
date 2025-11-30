(ns k8s.services.matrix.element-call.livekit-jwt.service)

(def config
  {:stack [:vault:prepare [:k8s :deployment :service :httproute]]
   :image-port     8080
   :app-namespace "matrix"
   :app-name      "livekit-jwt"
   :k8s:deployment-opts {:spec
                         {:template
                          {:spec
                           {:containers [{:name 'app-name :image '(str repo "/" "lk-jwt-service" ":latest")
                                          :env [{:name "LIVEKIT_KEY"    :value 'key-name}
                                                {:name "LIVEKIT_SECRET" :value 'dev-key}
                                                {:name "LIVEKIT_JWT_PORT" :value "8080"}
                                                {:name "LIVEKIT_URL"    :value 'livekit-url}]}]}}}}
   :k8s:httproute-opts
   {:spec
    {:hostnames ['host]
     :rules [{:matches [{:path {:type "PathPrefix" :value "/livekit/jwt"}}]
              :backendRefs [{:name 'app-name :port 80}]}
             {:matches [{:path {:type "PathPrefix" :value "/sfu/get"}}]
              :backendRefs [{:name 'app-name :port 80}]}]}}})