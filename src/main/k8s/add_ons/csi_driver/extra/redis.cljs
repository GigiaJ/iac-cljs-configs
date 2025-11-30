(ns k8s.add-ons.csi-driver.extra.redis)

(def config
  {:stack [:vault:prepare :k8s:secret :k8s:pvc :k8s:deployment :k8s:service]
   :app-name "juicefs-redis"
   :app-namespace "kube-system"
   :no-namespace true

   :k8s:pvc-opts
   {:metadata {:name "juicefs-redis-data"
               :namespace "kube-system"}
    :spec {:accessModes ["ReadWriteOnce"]
           :storageClassName "hcloud-volumes"
           :resources {:requests {:storage "10Gi"}}}}

   :k8s:deployment-opts
   {:metadata {:name "juicefs-redis" :namespace "kube-system"}
    :spec {:replicas 1
           :selector {:matchLabels {:app "juicefs-redis"}}
           :template {:metadata {:labels {:app "juicefs-redis"}}
                      :spec {:volumes [{:name "juicefs-redis-data"
                                        :persistentVolumeClaim
                                        {:claimName "juicefs-redis-data"}}]
                             :containers
                             [{:name "juicefs-redis"
                               :image "redis:7-alpine"
                               :args ["--requirepass" "$(REDIS_PASS)"
                                      "--maxmemory-policy" "noeviction"
                                      "--appendonly" "yes"]
                               :env [{:name "REDIS_PASS"
                                      :valueFrom {:secretKeyRef {:name "juicefs-redis-secrets"
                                                                 :key "password"}}}]
                               :ports [{:containerPort 6379}]
                               :volumeMounts [{:name "juicefs-redis-data"
                                               :mountPath "/data"}]
                               }]}}}}

   :k8s:service-opts
   {:metadata {:name "juicefs-redis" :namespace "kube-system"}
    :spec {:type "ClusterIP"
           :selector {:app "juicefs-redis"}
           :ports [{:name 'app-name
                    :port 6379 :targetPort 6379}]}}})