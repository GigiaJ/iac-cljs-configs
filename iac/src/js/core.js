const pulumi = require("@pulumi/pulumi");
const hcloud = require("@pulumi/hcloud");
const command = require("@pulumi/command/remote");
const k8s = require("@pulumi/kubernetes");
const local = require("@pulumi/command/local");
const fs = require("fs");


exports.createCluster = function() {
    const config = new pulumi.Config();
    const sshKeyName = config.require("sshKeyName");
    const privateKey = config.requireSecret("privateKeySsh");

 const installMasterScript = (publicIp) => `
        # Create the directory for custom manifests if it doesn't exist
        mkdir -p /var/lib/rancher/k3s/server/manifests

        # Create the Traefik configuration file to use NodePort
        cat <<EOF > /var/lib/rancher/k3s/server/manifests/traefik-config.yaml
apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata:
  name: traefik
  namespace: kube-system
spec:
  valuesContent: |-
    service:
      spec:
        type: NodePort
    ports:
      web:
        nodePort: 30080
      websecure:
        nodePort: 30443
EOF

        # Install k3s if it's not already present.
        if ! command -v k3s >/dev/null; then
          curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--flannel-backend=wireguard-native --node-external-ip=${publicIp}" sh -
        fi
        
        # Wait until the kubeconfig is readable and a kubectl command succeeds.
        until sudo k3s kubectl get node > /dev/null 2>&1; do
            echo "Waiting for master node to be ready..."
            sleep 5
        done
    `;

    const installWorkerScript = (masterIp, token) => `#!/bin/bash
# Redirect all output (stdout and stderr) to a log file for debugging.
exec > /root/k3s-install.log 2>&1

set -x # Echo every command being executed to the log file.

echo "--- Starting k3s worker installation script at $(date) ---"

# Add a loop to wait for network connectivity to the master node.
until ping -c1 ${masterIp}; do
    echo "Waiting for network connectivity to master..."
    sleep 2
done

echo "Network is up. Discovering this node's public IP..."
# Use an external service to find the public IP address of this server.
WORKER_PUBLIC_IP=$(curl -s https://ifconfig.me/ip)
# The dollar sign is escaped (\$) so it's interpreted by the remote shell.
echo "Discovered public IP: \${WORKER_PUBLIC_IP}"

echo "Proceeding with k3s agent installation."

if ! command -v k3s >/dev/null; then
  echo "k3s not found, attempting installation..."
  # Pass the discovered public IP to the k3s installer.
  # The dollar sign for WORKER_PUBLIC_IP is escaped (\$) so that it's interpreted
  # by the bash script on the remote server, not by the local Node.js process.
  curl -sfL https://get.k3s.io | K3S_URL=https://${masterIp}:6443 K3S_TOKEN="${token}" INSTALL_K3S_EXEC="--node-external-ip=\${WORKER_PUBLIC_IP}" sh -
  echo "k3s installation script finished with exit code $?."
else
  echo "k3s is already installed."
fi

echo "--- Finished k3s worker installation script at $(date) ---"
`;


    const firewall = new hcloud.Firewall("k3s-firewall", {
        rules: [
            { direction: "in", protocol: "tcp", port: "22", sourceIps: ["0.0.0.0/0", "::/0"] },
            { direction: "in", protocol: "tcp", port: "6443", sourceIps: ["0.0.0.0/0", "::/0"] },
            { direction: "in", protocol: "udp", port: "51820", sourceIps: ["0.0.0.0/0", "::/0"] },
            { direction: "in", protocol: "icmp", sourceIps: ["0.0.0.0/0", "::/0"] },
        ],
    });


    const master = new hcloud.Server("k3s-master-de", {
        serverType: "cx22",
        image: "ubuntu-22.04",
        location: "fsn1",
        sshKeys: [sshKeyName],
        firewallIds: [firewall.id],
    });

    const masterConnection = {
        host: master.ipv4Address,
        user: "root",
        privateKey: privateKey,
    };

    const installMaster = new command.Command("install-master", {
        connection: masterConnection,
        create: master.ipv4Address.apply(installMasterScript),
    }, { dependsOn: [master] });

    const tokenCmd = new command.Command("get-token", {
        connection: masterConnection,
        create: "sudo cat /var/lib/rancher/k3s/server/node-token",
    }, { dependsOn: [installMaster] });

    const workerScript = pulumi.all([master.ipv4Address, tokenCmd.stdout]).apply(([ip, token]) =>
        installWorkerScript(ip, token.trim())
    );

    const workerDe = new hcloud.Server("k3s-worker-de", {
        serverType: "cx22",
        image: "ubuntu-22.04",
        location: "fsn1",
        sshKeys: [sshKeyName],
        userData: workerScript,
        firewallIds: [firewall.id],
    });

    const workerUs = new hcloud.Server("k3s-worker-us", {
        serverType: "cpx11",
        image: "ubuntu-22.04",
        location: "ash",
        sshKeys: [sshKeyName],
        userData: workerScript,
        firewallIds: [firewall.id],
    });

    const kubeconfigCmd = new command.Command("get-kubeconfig", {
        connection: masterConnection,
        create: master.ipv4Address.apply(ip =>
            `sudo sed 's/127.0.0.1/${ip}/' /etc/rancher/k3s/k3s.yaml`
        ),
    }, { dependsOn: [installMaster] });

      const labelNodeCmd = new local.Command("label-german-node", {

        create: pulumi.all([kubeconfigCmd.stdout, workerDe.name]).apply(([kubeconfig, workerName]) => {

            const tempKubeconfigFile = "./kubeconfig.yaml";
            fs.writeFileSync(tempKubeconfigFile, kubeconfig);
            
            return `kubectl --kubeconfig=${tempKubeconfigFile} label node ${workerName} location=de --overwrite`;
        }),
    }, { dependsOn: [kubeconfigCmd] });

    return {
        masterIp: master.ipv4Address,
        workerDeIp: workerDe.ipv4Address,
        workerUsIp: workerUs.ipv4Address,
        kubeconfig: pulumi.secret(kubeconfigCmd.stdout),
    };
}
