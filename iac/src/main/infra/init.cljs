(ns infra.init
  (:require ["@pulumi/pulumi" :as pulumi]
            ["@pulumi/hcloud" :as hcloud]
            ["@pulumi/command/remote" :as remote]
            ["@pulumi/command/local" :as local]
            ["@pulumi/kubernetes" :as k8s]
            ["fs" :as fs]))

(defn- install-master-script [public-ip]
  (str "# Create manifests dir\n"
       "mkdir -p /var/lib/rancher/k3s/server/manifests\n\n"
       "# Traefik NodePort config\n"
       "cat <<EOF > /var/lib/rancher/k3s/server/manifests/traefik-config.yaml\n"
       "apiVersion: helm.cattle.io/v1\n"
       "kind: HelmChartConfig\n"
       "metadata:\n"
       "  name: traefik\n"
       "  namespace: kube-system\n"
       "spec:\n"
       "  valuesContent: |-\n"
       "    service:\n"
       "      spec:\n"
       "        type: NodePort\n"
       "    ports:\n"
       "      web:\n"
       "        nodePort: 30080\n"
       "      websecure:\n"
       "        nodePort: 30443\n"
       "EOF\n\n"
       "# Install k3s if not present\n"
       "if ! command -v k3s >/dev/null; then\n"
       "  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC=\"--flannel-backend=wireguard-native --node-external-ip=" public-ip "\" sh -\n"
       "fi\n\n"
       "# Wait for node readiness\n"
       "until sudo k3s kubectl get node >/dev/null 2>&1; do\n"
       "    echo 'Waiting for master node...'\n"
       "    sleep 5\n"
       "done\n"))

(defn- install-worker-script [master-ip token]
  (str "#!/bin/bash\n"
       "exec > /root/k3s-install.log 2>&1\n"
       "set -x\n"
       "echo '--- Starting worker install ---'\n\n"
       "until ping -c1 " master-ip "; do\n"
       "    echo 'Waiting for master...'\n"
       "    sleep 2\n"
       "done\n\n"
       "WORKER_PUBLIC_IP=$(curl -s https://ifconfig.me/ip)\n"
       "echo \"Public IP: $WORKER_PUBLIC_IP\"\n\n"
       "if ! command -v k3s >/dev/null; then\n"
       "  curl -sfL https://get.k3s.io | "
       "K3S_URL=https://" master-ip ":6443 "
       "K3S_TOKEN=\"" token "\" "
       "INSTALL_K3S_EXEC=\"--node-external-ip=$WORKER_PUBLIC_IP\" sh -\n"
       "fi\n\n"
       "echo '--- Finished worker install ---'\n"))

(defn create-cluster []
  (let [cfg        (pulumi/Config.)
        ssh-key    (.require cfg "sshKeyName")
        personal-key (.require cfg "sshPersonalKeyName")
        priv-key   (.requireSecret cfg "privateKeySsh")

        firewall   (hcloud/Firewall.
                    "k3s-firewall"
                    (clj->js {:rules [{:direction "in" :protocol "tcp" :port "22"    :sourceIps ["0.0.0.0/0" "::/0"]}
                                      {:direction "in" :protocol "tcp" :port "6443"  :sourceIps ["0.0.0.0/0" "::/0"]}
                                      {:direction "in" :protocol "udp" :port "51820" :sourceIps ["0.0.0.0/0" "::/0"]}
                                      {:direction "in" :protocol "icmp"             :sourceIps ["0.0.0.0/0" "::/0"]}]}))

        master     (hcloud/Server.
                    "k3s-master-de"
                    (clj->js {:serverType "cx22"
                              :image "ubuntu-22.04"
                              :location "fsn1"
                              :sshKeys [ssh-key personal-key]
                              :firewallIds [(.-id firewall)]}))

        master-ip  (.-ipv4Address master)

        master-conn (clj->js {:host master-ip
                              :user "root"
                              :privateKey priv-key})

        install-master
        (remote/Command.
         "install-master"
         (clj->js {:connection master-conn
                   :create (.apply master-ip install-master-script)})
         (clj->js {:dependsOn [master]}))

        token-cmd
        (remote/Command.
         "get-token"
         (clj->js {:connection master-conn
                   :create "sudo cat /var/lib/rancher/k3s/server/node-token"})
         (clj->js {:dependsOn [install-master]}))

       worker-script
       (.apply master-ip
               (fn [ip]
                 (.apply (.-stdout token-cmd)
                         (fn [token]
                           (install-worker-script ip (.trim token))))))

        worker-de  (hcloud/Server.
                    "k3s-worker-de"
                    (clj->js {:serverType "cx22"
                              :image "ubuntu-22.04"
                              :location "fsn1"
                              :sshKeys [ssh-key personal-key]
                              :userData worker-script
                              :firewallIds [(.-id firewall)]}))

        worker-us  (hcloud/Server.
                    "k3s-worker-us"
                    (clj->js {:serverType "cpx11"
                              :image "ubuntu-22.04"
                              :location "ash"
                              :sshKeys [ssh-key personal-key]
                              :userData worker-script
                              :firewallIds [(.-id firewall)]}))

        kubeconfig-cmd
        (remote/Command.
         "get-kubeconfig"
         (clj->js {:connection master-conn
                   :create (.apply master-ip
                                   (fn [ip]
                                     (str "sudo sed 's/127.0.0.1/" ip "/' /etc/rancher/k3s/k3s.yaml")))})
         (clj->js {:dependsOn [install-master worker-de worker-us]}))

        label-node
(local/Command.
 "label-german-node-alt"
 (clj->js {:create (.apply (.-stdout kubeconfig-cmd)
                           (fn [kubeconfig]
                             (.apply (.-name worker-de)
                                     (fn [worker-name]
                                       (let [path "./kubeconfig.yaml"]
                                         (.writeFileSync fs path kubeconfig)
                                         (str "kubectl --kubeconfig=" path
                                              " label node " worker-name
                                              " location=de --overwrite"))))))})
         (clj->js {:dependsOn [kubeconfig-cmd]}))]

    {:masterIp master-ip
              :workerDeIp (.-ipv4Address worker-de)
              :workerUsIp (.-ipv4Address worker-us)
              :kubeconfig (pulumi/secret (.-stdout kubeconfig-cmd))}))
