const k8s = require("@pulumi/kubernetes");
const fs = require("fs");
const path = require("path");
const yaml = require("js-yaml");

/**
 * Deploys HashiCorp Vault using the official Helm chart.
 * @param {k8s.Provider} provider - The Kubernetes provider to deploy resources with.
 */
exports.deployVault = function(provider) {
    const ns = new k8s.core.v1.Namespace("vault-ns", {
        metadata: { name: "vault" }
    }, { provider });

    const valuesYamlPath = path.join(__dirname, 'values.yaml');
    const valuesYaml = fs.readFileSync(valuesYamlPath, "utf8");
    const helmValues = yaml.load(valuesYaml);

    const vaultChart = new k8s.helm.v3.Chart("openbao", {
        chart: "openbao",
        fetchOpts: { repo: "https://openbao.github.io/openbao-helm" },
        namespace: ns.metadata.name,
        values: helmValues,
    }, {
        provider,
        dependsOn: [ns],
    });

    return { vaultNamespace: ns.metadata.name };
};
