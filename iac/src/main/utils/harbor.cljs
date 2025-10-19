(ns utils.harbor
  (:require
   [utils.general :refer [resource-factory component-factory]]
   [utils.vault :refer [retrieve]]
    ["uuid" :as uuid]
   ["@pulumiverse/harbor" :as harbor]))

(defn default-project [{:keys [name]}]
  {:name name
   :public false})

(defn default-robot [{:keys [name]}]
  {:name (str name "-robot")
   :level "project"
   :permissions [{:kind "project"
                  :namespace name
                  :access [{:action "push" :resource "repository"}
                           {:action "pull" :resource "repository"}
                           {:action "list" :resource "repository"}]}]})

(defn create-provider [final-args]
  (let [name (str "harbor-provider-" (uuid/v4))]
    (harbor/Provider. name final-args)))

(def default-resource-class-map
  {:project             (.. harbor -Project)
   :robot-account       (.. harbor -RobotAccount)})
(def create-resource (resource-factory default-resource-class-map))
(def create-component (component-factory create-resource))
 

(defn deploy-stack
  "Deploys a versatile stack of Harbor resources"
  [& args]
  (let [[component-kws [options]] (split-with keyword? args)
        requested-components (set component-kws)
        {:keys [provider vault-provider pulumi-cfg name harbor-app-name harbor-app-namespace project-opts robot-opts]} options
        prepared-vault-data (when (requested-components :vault-secrets) (retrieve vault-provider harbor-app-name harbor-app-namespace))
        {:keys [secrets]} (or prepared-vault-data {:secrets nil})
        project (create-component requested-components :project provider name (vec (filter some? [provider])) project-opts (default-project options) secrets options)
        robot-account (create-component requested-components :robot-account provider (str name "-robot") (vec (filter some? [provider project])) robot-opts (default-robot (assoc options :project project)) secrets options)]
    {:project project, :robot-account robot-account, :vault-secrets prepared-vault-data}))