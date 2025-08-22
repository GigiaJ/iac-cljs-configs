const pulumi = require("@pulumi/pulumi");
const k8s = require("@pulumi/kubernetes");

/**
 * Deploys the Hetzner CSI driver to the cluster.
 * @param {k8s.Provider} provider - The Kubernetes provider to deploy resources with.
 */
exports.deployCsiDriver = function(provider) {
    const hcloudConfig = new pulumi.Config("hcloud");
    const hcloudToken = hcloudConfig.requireSecret("token");
    const csiSecret = new k8s.core.v1.Secret("hcloud-csi-secret", {
        metadata: {
            name: "hcloud",
            namespace: "kube-system",
        },
        stringData: {
            token: hcloudToken,
        },
    }, { provider });

    const csiChart = new k8s.helm.v3.Chart("hcloud-csi", {
        chart: "hcloud-csi",
        fetchOpts: { repo: "https://charts.hetzner.cloud" },
        namespace: "kube-system",
        values: {
            controller: {
                secret: {
                    enabled: false,
                },
                existingSecret: {
                    name: csiSecret.metadata.name,
                }
            },
            node: {
                existingSecret: {
                    name: csiSecret.metadata.name,
                }
            }
        },
    }, {
        provider,
        dependsOn: [csiSecret],
    });

    return { csiChart };
};
