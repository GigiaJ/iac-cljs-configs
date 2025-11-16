(ns k8s.preparers.harbor)

(defn execute-fn [env]
  (let [docker-string (:docker-json-string env)]
    {:docker-string docker-string}))


(def config
  {:stack [:vault:retrieve [:harbor :project :robot-account] :k8s:secret]
   :no-namespace true
   :app-name "apps"
   :app-namespace "generic"
   :image-port 80
   :vault-load-yaml false
   :k8s:secret-opts {:metadata
                     {:name "harbor-creds-secrets"
                      :namespace "kube-system"
                      :annotations {"replicator.v1.mittwald.de/replicate-to" "*"}}
                     :type "kubernetes.io/dockerconfigjson"
                     :stringData {".dockerconfigjson" '(str "{\"auths\":{\""
                                                            host
                                                            "\":{\"auth\":\""
                                                            (b64e (str (-> :harbor:robot-account .-name) ":" (-> :harbor:robot-account .-secret)))
                                                            "\"}}}")}}
   :harbor:robot-opts {:name (str "kube" "-robot")
                       :namespace 'app-name
                       :level "project"
                       :permissions [{:kind "project"
                                      :namespace 'app-name
                                      :access [{:action "pull" :resource "repository"}
                                               {:action "list" :resource "repository"}]}]}
   :vault:retrieve-opts {:app-name "harbor"
                         :app-namespace "harbor"}})