(ns k8s.add-ons.ingress-controller.caddy)

(def config
  {:stack [:docker:docker-image :k8s:vault-secrets :k8s:chart]
   :app-namespace "caddy-system"
   :app-name      "caddy-ingress-controller"
   :k8s:image-port 8080
   :k8s:vault-load-yaml false
   :k8s:image-opts {:imageName '(str repo "/" app-name ":latest")}
   :k8s:chart-opts {:fetchOpts {:repo "https://caddyserver.github.io/ingress"}
                :values
                {:ingressController
                 {:deployment {:kind "DaemonSet"}
                  :daemonSet {:useHostPort true}
                  :ports {:web {:hostPort 80}
                          :websecure {:hostPort 443}}
                  :service {:type "NodePort"
                            :externalTrafficPolicy "Local"}
                  :image {:repository 'repo
                          :tag "latest"}
                  :config {:email 'email}}}}})