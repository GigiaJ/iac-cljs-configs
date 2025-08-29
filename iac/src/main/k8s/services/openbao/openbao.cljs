(ns k8s.services.openbao.openbao
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/command/local" :as local]
   ["fs" :as fs]
   ["uuid" :as uuid]
   ["js-yaml" :as yaml]
   ["path" :as path]))

(defn- create-port-forward-script [namespace service port]
  "Script to start kubectl port-forward in the background and wait for it to be ready."
  (str "#!/bin/bash\n"
       "set -e\n\n"
       "PID_FILE=\"/tmp/pulumi-pf-" service ".pid\"\n"
       "echo 'Starting port-forward for " service " in background...'\n"
       "# Start port-forward and send its output to a log file for debugging\n"
       "kubectl port-forward -n " namespace " svc/" service " " port ":" port " > /tmp/pulumi-pf-" service ".log 2>&1 &\n"
       "PORT_FORWARD_PID=$!\n\n"
       "# Save the PID so we can kill it later\n"
       "echo $PORT_FORWARD_PID > $PID_FILE\n\n"
       "echo \"Port-forward process started with PID: $PORT_FORWARD_PID\"\n\n"
       "# Wait for the port to become active. This is crucial!\n"
       "echo 'Waiting for port " port " to be open on localhost...'\n"
       "until curl --output /dev/null --silent --head --fail http://127.0.0.1:" port "; do\n"
       "    # Check if the process died unexpectedly\n"
       "    if ! kill -0 $PORT_FORWARD_PID 2>/dev/null; then\n"
       "        echo 'Error: Port-forward process died unexpectedly. Check logs at /tmp/pulumi-pf-" service ".log'\n"
       "        exit 1\n"
       "    fi\n"
       "    printf '.'\n"
       "    sleep 2\n"
       "done\n\n"
       "echo '\nPort-forward is active and ready!'\n"))

(defn- create-cleanup-port-forward-script [service]
  "Script to clean up the background port-forward process."
  (str "#!/bin/bash\n"
       "set -e\n\n"
       "PID_FILE=\"/tmp/pulumi-pf-" service ".pid\"\n"
       "if [ -f \"$PID_FILE\" ]; then\n"
       "    PID=$(cat $PID_FILE)\n"
       "    echo \"Cleaning up port-forward process with PID: $PID\"\n"
       "    # Kill the process and ignore errors if it's already gone\n"
       "    kill $PID 2>/dev/null || true\n"
       "    rm $PID_FILE\n"
       "else\n"
       "    echo 'PID file not found, nothing to clean up.'\n"
       "fi\n"))

(defn- create-init-script []
  "Script to initialize and unseal OpenBao with port-forward"
  (str "#!/bin/bash\n"
       "set -e\n\n"
       "# Wait for OpenBao to be ready\n"
       "echo 'Waiting for OpenBao to be ready...'\n"
       "until kubectl get pod -n vault -l app.kubernetes.io/name=openbao -o jsonpath='{.items[0].status.phase}' | grep Running; do\n"
       "    echo 'Waiting for OpenBao pod...'\n"
       "    sleep 10\n"
       "done\n\n"
       "# Wait a bit more for the service to be fully ready\n"
       "sleep 30\n\n"
       "# Start port-forward in background\n"
       "echo 'Starting port-forward to OpenBao...'\n"
       "kubectl port-forward -n vault svc/openbao 8200:8200 &\n"
       "PORT_FORWARD_PID=$!\n"
       "sleep 10  # Give port-forward time to establish\n\n"
       "# Function to cleanup port-forward\n"
       "cleanup() {\n"
       "    echo 'Cleaning up port-forward...'\n"
       "    kill $PORT_FORWARD_PID 2>/dev/null || true\n"
       "}\n"
       "trap cleanup EXIT\n\n"
       "# Set local vault address\n"
       "export BAO_ADDR='http://localhost:8200'\n\n"
       "# Check if already initialized\n"
       "if curl -s $BAO_ADDR/v1/sys/health | jq -r '.initialized' | grep -q 'true'; then\n"
       "    echo 'OpenBao already initialized'\n"
       "    cleanup\n"
       "    exit 0\n"
       "fi\n\n"
       "echo 'Initializing OpenBao...'\n"
       "# Initialize OpenBao using curl (since bao CLI might not be available locally)\n"
       "INIT_OUTPUT=$(curl -s -X POST $BAO_ADDR/v1/sys/init -d '{\"secret_shares\":1,\"secret_threshold\":1}')\n\n"
       "# Extract keys and root token\n"
       "UNSEAL_KEY=$(echo \"$INIT_OUTPUT\" | jq -r '.keys_base64[0]')\n"
       "ROOT_TOKEN=$(echo \"$INIT_OUTPUT\" | jq -r '.root_token')\n\n"
       "echo 'Unsealing OpenBao...'\n"
       "# Unseal OpenBao\n"
       "curl -s -X POST $BAO_ADDR/v1/sys/unseal -d '{\"key\":\"'$UNSEAL_KEY'\"}'\n\n"
       "# Save credentials to files for Pulumi to read\n"
       "echo \"$ROOT_TOKEN\" > /tmp/openbao-root-token\n"
       "echo \"$UNSEAL_KEY\" > /tmp/openbao-unseal-key\n\n"
       "echo 'OpenBao initialization complete!'\n"
       "echo \"Root token saved to /tmp/openbao-root-token\"\n"
       "echo \"Unseal key saved to /tmp/openbao-unseal-key\"\n\n"
       "cleanup\n"))

(defn- create-setup-secrets-script []
  "Script to set up initial secrets in OpenBao using port-forward"
  (str "#!/bin/bash\n"
       "set -e\n\n"
       "ROOT_TOKEN=$(cat /tmp/openbao-root-token)\n\n"
       "# Start port-forward in background\n"
       "echo 'Starting port-forward for secrets setup...'\n"
       "kubectl port-forward -n vault svc/openbao 8200:8200 &\n"
       "PORT_FORWARD_PID=$!\n"
       "sleep 10\n\n"
       "# Function to cleanup port-forward\n"
       "cleanup() {\n"
       "    kill $PORT_FORWARD_PID 2>/dev/null || true\n"
       "}\n"
       "trap cleanup EXIT\n\n"
       "export BAO_ADDR='http://localhost:8200'\n"
       "export BAO_TOKEN=\"$ROOT_TOKEN\"\n\n"
       "echo 'Setting up OpenBao secrets...'\n"
       "# Enable KV secrets engine\n"
       "curl -s -H \"X-Vault-Token: $BAO_TOKEN\" -X POST $BAO_ADDR/v1/sys/mounts/secret -d '{\"type\":\"kv-v2\"}'\n\n"
       "# Create Nextcloud secrets\n"
       "curl -s -H \"X-Vault-Token: $BAO_TOKEN\" -X POST $BAO_ADDR/v1/secret/data/nextcloud -d '{\n"
       "  \"data\": {\n"
       "    \"adminPassword\": \"admin-password-change-me\",\n"
       "    \"dbPassword\": \"db-password-change-me\",\n"
       "    \"host\": \"nextcloud.example.com\"\n"
       "  }\n"
       "}'\n\n"
       "echo 'OpenBao secrets setup complete!'\n"
       "cleanup\n"))

(defn deploy-vault
  "Deploy OpenBao via Helm chart with automated initialization."
  [provider kubeconfig]
  (let [core-v1 (.. k8s -core -v1)
        helm-v3 (.. k8s -helm -v3)
        apps-v1 (.. k8s -apps -v1)

        vault-ns (new (.. core-v1 -Namespace)
                      "vault-ns"
                      (clj->js {:metadata {:name "vault"}})
                      (clj->js {:provider provider}))

        values-path (.join path js/__dirname "resources" "openbao.yml")
        helm-values (-> values-path
                        (fs/readFileSync "utf8")
                        (yaml/load))

        chart (new (.. helm-v3 -Chart)
                   "openbao"
                   (clj->js {:chart "openbao"
                             :fetchOpts {:repo "https://openbao.github.io/openbao-helm"}
                             :namespace (.. vault-ns -metadata -name)
                             :values helm-values})
                   (clj->js {:provider provider
                             :dependsOn [vault-ns]}))

        wait-for-deployment
        (new (.. k8s -core -v1 -Service)  ; Using a dummy service as a dependency marker
             "openbao-ready-marker"
             (clj->js {:metadata {:name "openbao-ready"
                                  :namespace (.. vault-ns -metadata -name)
                                  :labels {:app "openbao-init"}}
                       :spec {:selector {:app "nonexistent"}  ; Dummy selector
                              :ports [{:port 80}]}})
             (clj->js {:provider provider
                       :dependsOn [chart]}))

        init-command
        (local/Command.
         "openbao-init"
         (clj->js {:create (create-init-script)
                   :environment (clj->js {:KUBECONFIG kubeconfig})})
         (clj->js {:dependsOn [wait-for-deployment]}))

        setup-secrets
        (local/Command.
         "openbao-setup-secrets"
         (clj->js {:create (create-setup-secrets-script)
                   :environment (clj->js {:KUBECONFIG kubeconfig})})
         (clj->js {:dependsOn [init-command]}))

        port-forward-manager
        (local/Command.
         "manage-openbao-port-forward"
         (clj->js {:create (create-port-forward-script "vault" "openbao" "8200")
                   :delete (create-cleanup-port-forward-script "openbao")
                   :triggers [(uuid/v4)]
                   :environment (clj->js {:KUBECONFIG kubeconfig})})
        (clj->js {:dependsOn [setup-secrets]}))

        root-token-cmd
        (local/Command.
         "get-root-token"
         (clj->js {:create "cat /tmp/openbao-root-token"})
         (clj->js {:dependsOn [init-command]}))]

    (clj->js {:namespace vault-ns
              :chart chart
              :root-token (.-stdout root-token-cmd)
              :address "http://127.0.0.1:8200"
              :init-command init-command
              :setup-secrets setup-secrets
              :port-forward-manager port-forward-manager})))

(defn configure-vault-access
  "Configure Pulumi config with OpenBao credentials after deployment"
  [openbao-deployment]
  (let [config-cmd
        (local/Command.
         "configure-pulumi-vault"
         (clj->js {:create (.apply (aget openbao-deployment "root_token")
                                   (fn [token]
                                     (str "pulumi config set vault:address 'http://127.0.0.1:8200'\n"
                                          "pulumi config set --secret vault:token '" token "'")))})
         (clj->js {:dependsOn [(aget openbao-deployment "setup_secrets")]}))]
    config-cmd))