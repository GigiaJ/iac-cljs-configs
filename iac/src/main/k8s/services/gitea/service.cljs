(ns k8s.services.gitea.service)

(def config
  {:stack [:vault:prepare :k8s:deployment :k8s:service :k8s:httproute]
   :image-port    3000
   :app-namespace "generic"
   :app-name      "gitea"
   :k8s:deployment-opts {:spec {:template {:spec {:initContainers [
                                                               {:name "init-permissions"
                                                                :image "busybox:latest"
                                                                :command ["sh" "-c" "chown -R 1000:1000 /var/lib/gitea && chown -R 1000:1000 /etc/gitea"]
                                                                :volumeMounts [{:name "gitea-data" :mountPath "/var/lib/gitea"}
                                                                               {:name "gitea-config" :mountPath "/etc/gitea"}]
                                                                :securityContext {:runAsUser 0 :runAsGroup 0}}
                                                               ]
                                              :containers [{:name 'app-name :image '(str repo "/" app-name ":latest-rootless")
                                                            :volumeMounts [{:name "gitea-data" :mountPath "/var/lib/gitea"}
                                                                           {:name "gitea-config" :mountPath "/etc/gitea"}
                                                                           {:name "timezone" :mountPath "/etc/timezone" :readOnly true}
                                                                           {:name "localtime" :mountPath "/etc/localtime" :readOnly true}]}]
                                              :volumes [{:name "gitea-data" :hostPath {:path "/opt/gitea/data" :type "DirectoryOrCreate"}}
                                                        {:name "gitea-config" :hostPath {:path "/opt/gitea/config" :type "DirectoryOrCreate"}}
                                                        {:name "timezone" :hostPath {:path "/etc/timezone" :type "File"}}
                                                        {:name "localtime" :hostPath {:path "/etc/localtime" :type "File"}}]}}}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}})
