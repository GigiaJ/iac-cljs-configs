(ns k8s.services.act-runner.service)


(def config
  {:stack [:vault:prepare [:k8s :deployment :service]]
   :image-port     80
   :app-namespace "generic"
   :app-name      "act-runner"
   :k8s:deployment-opts {:spec {:template {:spec {:containers [{:name 'app-name
                                                            :envFrom [{:secretRef {:name '(str app-name "-secrets")}}]
                                                            :image '(str repo "/" "act_runner" ":latest")
                                                            }]}}}}
   :k8s:httproute-opts {:spec {::hostnames ['host]}}
   })