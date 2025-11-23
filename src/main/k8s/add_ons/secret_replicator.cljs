(ns k8s.add-ons.secret-replicator)

(def config
  {:stack [:k8s:chart]
   :image-port     80
   :no-namespace true
   :app-namespace "kube-system"
   :app-name      "kubernetes-replicator"
   :k8s:chart-opts {:fetchOpts {:repo "https://helm.mittwald.de"}}})