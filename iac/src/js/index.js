const k8s = require("@pulumi/kubernetes");
const core = require("./core");
const vault = require("./k8/openbao/openbao");
const nextcloud = require("./k8/nextcloud/nextcloud");
const hetznercsi = require('./k8/csi-drivers/hetzner');

async function main() {
    const cluster = core.createCluster();

    const appOutputs = cluster.kubeconfig.apply(async (kc) => {
        const provider = new k8s.Provider("k8s-dynamic-provider", {
            kubeconfig: kc,
        });

        hetznercsi.deployCsiDriver(provider);
        vault.deployVault(provider);

        const app = await nextcloud.deployNextcloudApp(kc, provider);
        return {
        nextcloudUrl: app.nextcloudUrl,
        };
    });

    return {
        masterIp: cluster.masterIp,
        kubeconfig: cluster.kubeconfig,
        nextcloudUrl: appOutputs.nextcloudUrl,
    };
}

module.exports = main();
