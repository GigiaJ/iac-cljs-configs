const pulumi = require("@pulumi/pulumi");
const k8s = require("@pulumi/kubernetes");
const vault = require("@pulumi/vault");
const fs = require("fs");
const path = require("path");
const yaml = require("js-yaml");

/**
 * Deploys the Nextcloud application using secrets from a running Vault instance.
 * @param {string} kubeconfig - The kubeconfig content for the cluster.
 * @param {k8s.Provider} provider - The Kubernetes provider to deploy resources with.
 */
exports.deployNextcloudApp = async function(kubeconfig, provider) {

    const vaultConfig = new pulumi.Config("vault");
    const vaultAddress = vaultConfig.require("address");
    const vaultToken = vaultConfig.requireSecret("token");

    const vaultProvider = new vault.Provider("vault-provider", {
        address: vaultAddress,
        token: vaultToken,
    });

    const nextcloudSecrets = vault.generic.getSecret({
        path: "secret/nextcloud",
    }, { provider: vaultProvider });

    const ns = new k8s.core.v1.Namespace("nextcloud-ns", {
        metadata: { name: "nextcloud" }
    }, { provider });

    const adminSecret = new k8s.core.v1.Secret("nextcloud-admin-secret-exact", {
        metadata: {
            name: "nextcloud-admin-secret",
            namespace: ns.metadata.name
        },
        stringData: {
            password: nextcloudSecrets.then(s => s.data["adminPassword"]),
        },
    }, { provider });

    const dbSecret = new k8s.core.v1.Secret("nextcloud-db-secret-exact", {
        metadata: {
            name: "nextcloud-db-secret",
            namespace: ns.metadata.name
        },
        stringData: {
            "mariadb-root-password": nextcloudSecrets.then(s => s.data["dbPassword"]),
            "mariadb-password": nextcloudSecrets.then(s => s.data["dbPassword"]),
        },
    }, { provider });

    const valuesYamlPath = path.join(__dirname, 'values.yaml');
    const valuesYaml = fs.readFileSync(valuesYamlPath, "utf8");
    const helmValues = yaml.load(valuesYaml);
    helmValues.ingress.hosts[0].host = nextcloudSecrets.then(s => s.data["host"]);

    const nextcloudChart = new k8s.helm.v3.Chart("my-nextcloud", {
        chart: "nextcloud",
        fetchOpts: { repo: "https://nextcloud.github.io/helm/" },
        namespace: ns.metadata.name,
        values: helmValues,
    }, {
        provider,
        dependsOn: [adminSecret, dbSecret],
    });

    return {
        nextcloudUrl: nextcloudSecrets.then(s => `https://${s.data["host"]}`),
    };
};
