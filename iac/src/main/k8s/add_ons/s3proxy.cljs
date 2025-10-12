(ns k8s.add-ons.s3proxy)

(def config
  {:stack [:vault-secrets :deployment :service :ingress]
   :app-namespace "s3proxy"
   :app-name      "s3proxy"
   :image-port    80
   :image         "andrewgaul/s3proxy:latest"
   :load-yaml     false
   :deployment-opts
   {:spec
    {:template
     {:spec
      {:containers
       [{:name "s3proxy"
         :env [{:name "S3PROXY_AUTHORIZATION" :value "none"}
               {:name "S3PROXY_ENDPOINT" :value "http://0.0.0.0:80"}
               ;;{:name "S3PROXY_IDENTITY" :value "local-identity"}
               ;;{:name "S3PROXY_CREDENTIAL" :value "local-credential"}
               {:name "JCLOUDS_PROVIDER" :value "s3"}
               {:name "JCLOUDS_IDENTITY" :valueFrom {:secretKeyRef {:name "s3proxy-secrets"
                                                                    :key "S3PROXY_IDENTITY"}}}
               {:name "JCLOUDS_CREDENTIAL" :valueFrom {:secretKeyRef {:name "s3proxy-secrets"
                                                                      :key "S3PROXY_CREDENTIAL"}}}
               {:name "JCLOUDS_ENDPOINT" :value "https://s3.wasabisys.com"}
               {:name "JCLOUDS_REGION" :value "us-east-1"}
               ]}]
       :nodeSelector {"node-role.kubernetes.io/master" "true"}}}}}})
