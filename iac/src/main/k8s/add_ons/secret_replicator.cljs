(ns k8s.add-ons.secret-replicator)

(def config
  {:stack [:chart]
   :image-port     80
   :no-namespace true
   :app-namespace "kube-system"
   :app-name      "kubernetes-replicator"
   :chart-opts {:fetchOpts {:repo "https://helm.mittwald.de"}}})