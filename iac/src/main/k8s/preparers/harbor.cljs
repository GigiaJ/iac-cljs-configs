(ns k8s.preparers.harbor
  (:require
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/command/local" :as local]
   ["@pulumiverse/harbor" :as harbor]
   [utils.harbor :refer [deploy-stack]]
   [utils.k8s :refer [default-secret create-component]]))

(defn login [harbor-provider vault-provider url]
  (let [harbor-resources (deploy-stack
                          :vault-secrets :project :robot-account
                          {:provider harbor-provider
                           :vault-provider vault-provider
                           :harbor-app-name "harbor"
                           :harbor-app-namespace "harbor"
                           :name "apps"})
        robot-account (:robot-account harbor-resources)
        robot-username (.-fullName robot-account)
        robot-password (.-secret robot-account)
        login-cmd (.apply (pulumi/all (clj->js [robot-username robot-password]))
                          (fn [values]
                            (let [[username password] (js->clj values)
                                  host-str url]
                              (str "printf \"%s\" " password " | docker login " host-str
                                   " --username '" username "' --password-stdin"))))]
    (new local/Command "docker-login-to-harbor"
         (clj->js {:create login-cmd})
         (clj->js {:dependsOn [robot-account]}))))



(defn create-pull-robot-secret [provider harbor-provider vault-provider url]
  (let [harbor-resources (deploy-stack
                          :vault-secrets :robot-account
                          {:provider harbor-provider
                           :vault-provider vault-provider
                           :robot-opts {:name (str "kube" "-robot")
                                        :namespace "apps"
                                        :level "project"
                                        :permissions [{
                                                       :kind "project"
                                                       :namespace "apps"
                                                       :access [{:action "pull" :resource "repository"}
                                                                {:action "list" :resource "repository"}]}]}
                           :harbor-app-name "harbor"
                           :harbor-app-namespace "harbor"
                           :name "kube"})
        robot-account (:robot-account harbor-resources)
        robot-username (.-fullName robot-account)
        robot-password (.-secret robot-account)]
    (.apply (pulumi/all (clj->js [robot-username robot-password]))
            (fn [values]
              (let [[username password] (js->clj values)
                    auth-str (-> (.from js/Buffer (str username ":" password))
                                 (.toString "base64"))
                    docker-config-map {:auths (hash-map url
                                                        {:auth auth-str})}
                    docker-json-string (.stringify js/JSON (clj->js docker-config-map))
                    secret-opts {:metadata {:annotations {"replicator.v1.mittwald.de/replicate-to" "*"}}
                                 :type "kubernetes.io/dockerconfigjson"
                                 :stringData {".dockerconfigjson" docker-json-string}}
                    app-name "harbor-creds"
                    app-namespace "kube-system"]
                (create-component #{:secret} :secret provider app-name nil secret-opts (default-secret {:app-name app-name :app-namespace app-namespace}) nil {}))))))

(defn merged-callbacks [provider harbor-provider vault-provider url]
  (login harbor-provider vault-provider url)
  (create-pull-robot-secret provider harbor-provider vault-provider url))

(defn generate-robot-account [provider-inputs provider vault-provider callback-fns]
  (.apply provider-inputs
          (fn [values]
            (let [[url username password] (js->clj values)
                  harbor-provider (harbor/Provider.
                            "harbor-provider"
                            (clj->js {:url (str "https://" url)
                                      :username username
                                      :password password}))]
              (callback-fns provider harbor-provider vault-provider url)))))

(defn execute-fn [{:keys [provider vault-provider]}]
  (let [stack-ref (new pulumi/StackReference "shared")
        harbor-provider-inputs (pulumi/all
                                (clj->js [(.getOutput stack-ref "url")
                                          (.getOutput stack-ref "username")
                                          (.getOutput stack-ref "password")]))]
    (generate-robot-account harbor-provider-inputs provider vault-provider merged-callbacks)))

(def config
  {:stack [:execute]
   :no-namespace true
   :app-name "harbor"
   :app-namespace "harbor"
   :image-port 80
   :vault-load-yaml false
   :exec-fn execute-fn})