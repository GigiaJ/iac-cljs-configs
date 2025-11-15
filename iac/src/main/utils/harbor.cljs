(ns utils.harbor
  (:require
   [utils.general :refer [resource-factory component-factory deploy-stack-factory iterate-stack]]
   [utils.vault :refer [retrieve]]
   ["uuid" :as uuid]
   ["@pulumiverse/harbor" :as harbor]))

(defn project [{:keys [app-name]}]
  {:name app-name
   :public false})

(defn robot [{:keys [app-name]}]
  {:name (str app-name "-robot")
   :level "project"
   :permissions [{:kind "project"
                  :namespace app-name
                  :access [{:action "push" :resource "repository"}
                           {:action "pull" :resource "repository"}
                           {:action "list" :resource "repository"}]}]})

(def defaults
  {:project project
   :robot robot})

(def provider-template
  {:constructor (.. harbor -Provider)
   :name "harbor-provider"
   :config {:url      'url
            :username 'username
            :password 'password}})


(def component-specs-defs
  {:root-sym 'harbor
   :provider-key :harbor
   :resources
   {:project       {:path ['-Project]}
    :robot-account {:path ['-RobotAccount]
                    :defaults-name 'robot}}})