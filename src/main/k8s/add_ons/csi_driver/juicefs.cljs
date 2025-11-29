(ns k8s.add-ons.csi-driver.juicefs)

(def config
  {:stack [:vault:prepare :k8s:secret :k8s:chart :k8s:csi-driver :k8s:storage-class]
   :app-namespace "kube-system"
   :no-namespace true
   :app-name "juicefs-csi"
   :k8s:csi-driver-opts
   {:metadata {:name "csi.juicefs.com"}
    :spec {:attachRequired false
           :podInfoOnMount true
           :volumeLifecycleModes ["Persistent"]}}

   :k8s:chart-opts
   {:repositoryOpts {:repo "https://juicedata.github.io/charts/"}
    :chart "juicefs-csi-driver"
    :version "0.30.3"
    :namespace "kube-system"
    :values {:kubeletDir "/var/lib/kubelet"}}
   :k8s:storage-class-opts
   {:metadata {:name "juicefs-sc"}
    :provisioner "csi.juicefs.com"
    :parameters {"csi.storage.k8s.io/provisioner-secret-name" "juicefs-csi-secrets"
                 "csi.storage.k8s.io/provisioner-secret-namespace" "kube-system"
                 "csi.storage.k8s.io/node-publish-secret-name" "juicefs-csi-secrets"
                 "csi.storage.k8s.io/node-publish-secret-namespace" "kube-system"
                 "csi.storage.k8s.io/controller-expand-secret-name" "juicefs-csi-secrets"
                 "csi.storage.k8s.io/controller-expand-secret-namespace" "kube-system"
                 "pathPattern" "${.pvc.namespace}/${.pvc.name}"}}})
