(ns k8s.add-ons.cert-manager)

(def config
  {:stack [:vault:prepare [:k8s :secret :chart :cluster-issuer]]
   :app-namespace "cert-manager"
   :app-name "cert-manager"
   :is-prod? true
   :k8s:chart-opts {:fetchOpts {:repo "https://charts.jetstack.io"}
                    :chart "cert-manager"
                    :version "v1.15.0"
                    :namespace "cert-manager"
                    :values {:installCRDs true}}
   :k8s:secret-opts {:metadata {:name "api-token-secret"}
                     :stringData {:apiToken 'token}}
   :k8s:cluster-issuer-opts {:spec {:acme {:email 'email
                                      :solvers [{:dns01 {:cloudflare {:apiTokenSecretRef {:name "api-token-secret" :key "apiToken"}}}
                                                 :selector {:dnsZones '(parse domains)}}]}}}
   })
