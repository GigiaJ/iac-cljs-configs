(ns infra.core
  (:require ["@pulumi/pulumi" :as pulumi]
            ["@pulumi/hcloud" :as hcloud]
            ["@pulumi/command/remote" :as command]
            ["@pulumi/kubernetes" :as k8s]))

(def config (pulumi/Config.))
(def ssh-key-name (.require config "sshKeyName"))
(def private-key (.requireSecret config "privateKeySsh"))

(defn install-master-script [public-ip]
  (str "if ! command -v k3s >/dev/null; then\n"
       "  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC=\"--flannel-backend=wireguard-native --node-external-ip="
       public-ip
       "\" sh -\n"
       "fi"))

(defn install-worker-script [master-ip token]
  (pulumi/interpolate
   (str "if ! command -v k3s >/dev/null; then\n"
        "  curl -sfL https://get.k3s.io | K3S_URL=https://"
        master-ip
        ":6443 K3S_TOKEN=\"" token "\" sh -\n"
        "fi")))

(defn hcloud-server [name server-type location ssh-key & {:keys [user-data]}]
  (hcloud/Server. name
                  #js {:serverType server-type
                       :image "ubuntu-22.04"
                       :location location
                       :sshKeys #js [ssh-key]
                       :userData user-data}))

(defn ssh-connection [ip]
  #js {:host ip
       :user "root"
       :privateKey private-key})

(defn main! []
  (let [
        master (hcloud-server "k3s-master-de" "cx22" "fsn1" ssh-key-name)
        master-conn (.apply (.-ipv4Address master) ssh-connection)

        install-master (command/Command. "install-master"
                                         #js {:connection master-conn
                                              :create (.apply (.-ipv4Address master)
                                                              install-master-script)})

        token-cmd (-> (.-stdout install-master)
                      (.apply (fn [_]
                                (command/Command. "get-token"
                                                  #js {:connection master-conn
                                                       :create "cat /var/lib/rancher/k3s/server/node-token"}))))

        token-stdout (-> token-cmd (.apply (fn [cmd] (.-stdout cmd))))


        worker-script (install-worker-script (.-ipv4Address master) token-stdout)

        worker-de (hcloud-server "k3s-worker-de" "cx22" "fsn1" ssh-key-name :user-data worker-script)
        worker-us (hcloud-server "k3s-worker-us" "cpx11" "ash" ssh-key-name :user-data worker-script)
        
        kubeconfig-cmd (-> (pulumi/all #js [(.-stdout install-master) (.-ipv4Address master)])
                           (.apply (fn [[_ master-ip]]
                                     (command/Command. "get-kubeconfig"
                                                       #js {:connection master-conn
                                                            :create (str "sleep 10 &&" "sed 's/127.0.0.1/" master-ip "/' /etc/rancher/k3s/k3s.yaml")}))))

        kubeconfig-stdout (-> kubeconfig-cmd (.apply (fn [cmd] (.-stdout cmd))))


        all-workers-ready (pulumi/all #js [(.-urn worker-de) (.-urn worker-us)])


        ready-kubeconfig (pulumi/all #js [kubeconfig-stdout all-workers-ready]
                                     (fn [[kc _]] kc))

        k8s-provider (k8s/Provider. "k8s-provider"
                                    #js {:kubeconfig ready-kubeconfig})]

    (-> (pulumi/all #js [(.-ipv4Address master)
                         (.-ipv4Address worker-de)
                         (.-ipv4Address worker-us)
                         kubeconfig-stdout])
        (.apply (fn [[master-ip worker-de-ip worker-us-ip kc]]
                  (js-obj
                   "masterIp"   master-ip
                   "workerDeIp" worker-de-ip
                   "workerUsIp" worker-us-ip
                   "kubeconfig" (pulumi/secret kc)))))))


(set! (.-main js/module) main!)
