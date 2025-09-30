(ns k8s.services.openbao.service
  (:require
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/command/local" :as local]
   ["fs" :as fs]
   ["uuid" :as uuid]
   ["js-yaml" :as yaml]
   ["path" :as path]))

(defn- create-wait-for-ready-script [namespace]
  "Script to wait for OpenBao pod to exist, then to be running, then for the service to be operational."
  (str "#!/bin/bash\n"
       "set -e\n\n"
       "NAMESPACE=\"" namespace "\"\n"
       "MAX_RETRIES=60\n"
       "RETRY_INTERVAL=10\n\n"

       "## -- NEW SECTION: Wait for the pod to even exist -- ##\n"
       "echo 'Waiting for OpenBao pod to be created...'\n"
       "POD_FOUND=false\n"
       "for i in $(seq 1 $MAX_RETRIES); do\n"
       "    # Check if a pod with the label exists. We redirect output to /dev/null.\n"
       "    if kubectl get pod -l app.kubernetes.io/instance=openbao -n \"$NAMESPACE\" -o jsonpath='{.items[0].metadata.name}' >/dev/null 2>&1; then\n"
       "        echo 'Pod has been created.'\n"
       "        POD_FOUND=true\n"
       "        break\n"
       "    fi\n"
       "    echo \"Attempt $i/$MAX_RETRIES: Pod not found yet, retrying in $RETRY_INTERVAL seconds...\"\n"
       "    sleep $RETRY_INTERVAL\n"
       "done\n\n"
       "if [ \"$POD_FOUND\" = false ]; then\n"
       "    echo 'Error: Timed out waiting for OpenBao pod to be created.' >&2\n"
       "    exit 1\n"
       "fi\n"
       "## -- END NEW SECTION -- ##\n\n"

       "echo 'Waiting for OpenBao pod to enter Running state...'\n"
       ;; Now this command is safe to run because we know the pod exists.
       "kubectl wait --for=jsonpath='{.status.phase}'=Running pod -l app.kubernetes.io/instance=openbao -n \"$NAMESPACE\" --timeout=600s\n\n"

       "echo 'Pod is Running. Now waiting for OpenBao service to be fully operational...'\n"
       "for i in $(seq 1 $MAX_RETRIES); do\n"
       "    echo \"Attempt $i/$MAX_RETRIES: Checking if OpenBao is responding...\"\n"
       "    \n"
       "    # Start a temporary port-forward to test connectivity\n"
       "    kubectl port-forward -n \"$NAMESPACE\" svc/openbao 8200:8200 &\n"
       "    PF_PID=$!\n"
       "    sleep 5 # Give port-forward a moment to establish\n"
       "    \n"
       "    # Test if OpenBao health endpoint responds\n"
       "    if curl -s --max-time 5 http://127.0.0.1:8200/v1/sys/health >/dev/null 2>&1; then\n"
       "        echo 'OpenBao is responding!'\n"
       "        kill $PF_PID 2>/dev/null || true\n"
       "        sleep 2  # Let port-forward cleanup\n"
       "        exit 0\n"
       "    fi\n"
       "    \n"
       "    kill $PF_PID 2>/dev/null || true\n"
       "    echo '   (not yet responding, will retry...)'\n"
       "    sleep $RETRY_INTERVAL\n"
       "done\n\n"
       "echo 'OpenBao failed to become ready after maximum retries'\n"
       "exit 1\n"))

(defn- create-init-script [namespace]
  "Robust script to initialize and unseal OpenBao with proper error handling"
  (str "#!/bin/bash\n"
       "set -e\n\n"
       "NAMESPACE=\"" namespace "\"\n"
       "BAO_ADDR='http://127.0.0.1:8200'\n"
       "PID_FILE=\"/tmp/openbao-pf.pid\"\n\n"
       "# Cleanup function\n"
       "cleanup() {\n"
       "    echo '🧹 Cleaning up...'\n"
       "    if [ -f \"$PID_FILE\" ]; then\n"
       "        PID=$(cat \"$PID_FILE\")\n"
       "        kill $PID 2>/dev/null || true\n"
       "        rm -f \"$PID_FILE\"\n"
       "    fi\n"
       "}\n"
       "trap cleanup EXIT\n\n"
       "# Start port-forward in background with better error handling\n"
       "echo 'Starting port-forward...'\n"
       "kubectl port-forward -n \"$NAMESPACE\" svc/openbao 8200:8200 > /tmp/pf.log 2>&1 &\n"
       "echo $! > \"$PID_FILE\"\n\n"
       "# Wait for port-forward to be ready with timeout\n"
       "echo 'Waiting for port-forward to be active...'\n"
       "for i in {1..30}; do\n"
       "    if curl -s --max-time 2 \"$BAO_ADDR/v1/sys/health\" >/dev/null 2>&1; then\n"
       "        echo 'Port-forward is active!'\n"
       "        break\n"
       "    fi\n"
       "    if [ $i -eq 30 ]; then\n"
       "        echo 'Port-forward failed to become active'\n"
       "        echo 'Port-forward log:'\n"
       "        cat /tmp/pf.log || true\n"
       "        exit 1\n"
       "    fi\n"
       "    printf '.'\n"
       "    sleep 2\n"
       "done\n\n"
       "# Check initialization status\n"
       "echo 'Checking OpenBao initialization status...'\n"
       "HEALTH_RESPONSE=$(curl -s \"$BAO_ADDR/v1/sys/health\" || echo '{}')\n"
       "INITIALIZED=$(echo \"$HEALTH_RESPONSE\" | jq -r '.initialized // false')\n"
       "SEALED=$(echo \"$HEALTH_RESPONSE\" | jq -r '.sealed // true')\n\n"
       "echo \"Current status: initialized=$INITIALIZED, sealed=$SEALED\"\n\n"
       "if [ \"$INITIALIZED\" = \"false\" ]; then\n"
       "    echo 'Initializing OpenBao...'\n"
       "    \n"
       "    INIT_RESPONSE=$(curl -s -w '%{http_code}' -X POST \"$BAO_ADDR/v1/sys/init\" \\\n"
       "        -H 'Content-Type: application/json' \\\n"
       "        -d '{\"secret_shares\":1,\"secret_threshold\":1}')\n"
       "    \n"
       "    HTTP_CODE=${INIT_RESPONSE: -3}\n"
       "    INIT_DATA=${INIT_RESPONSE%???}\n"
       "    \n"
       "    if [ \"$HTTP_CODE\" != \"200\" ]; then\n"
       "        echo \"Failed to initialize OpenBao. HTTP code: $HTTP_CODE\"\n"
       "        echo \"Response: $INIT_DATA\"\n"
       "        exit 1\n"
       "    fi\n"
       "    \n"
       "    UNSEAL_KEY=$(echo \"$INIT_DATA\" | jq -r '.keys_base64[0]')\n"
       "    ROOT_TOKEN=$(echo \"$INIT_DATA\" | jq -r '.root_token')\n"
       "    \n"
       "    if [ \"$UNSEAL_KEY\" = \"null\" ] || [ \"$ROOT_TOKEN\" = \"null\" ]; then\n"
       "        echo 'Failed to extract keys from initialization response'\n"
       "        echo \"Response: $INIT_DATA\"\n"
       "        exit 1\n"
       "    fi\n"
       "    \n"
       "    echo 'OpenBao initialized successfully!'\n"
       "    \n"
       "    # Save credentials securely\n"
       "    echo \"$ROOT_TOKEN\" > /tmp/openbao-root-token\n"
       "    echo \"$UNSEAL_KEY\" > /tmp/openbao-unseal-key\n"
       "    chmod 600 /tmp/openbao-root-token /tmp/openbao-unseal-key\n"
       "    \n"
       "    echo 'Unsealing OpenBao...'\n"
       "    UNSEAL_RESPONSE=$(curl -s -w '%{http_code}' -X POST \"$BAO_ADDR/v1/sys/unseal\" \\\n"
       "        -H 'Content-Type: application/json' \\\n"
       "        -d \"{\\\"key\\\":\\\"$UNSEAL_KEY\\\"}\")\n"
       "    \n"
       "    UNSEAL_HTTP_CODE=${UNSEAL_RESPONSE: -3}\n"
       "    UNSEAL_DATA=${UNSEAL_RESPONSE%???}\n"
       "    \n"
       "    if [ \"$UNSEAL_HTTP_CODE\" != \"200\" ]; then\n"
       "        echo \"Failed to unseal OpenBao. HTTP code: $UNSEAL_HTTP_CODE\"\n"
       "        echo \"Response: $UNSEAL_DATA\"\n"
       "        exit 1\n"
       "    fi\n"
       "    \n"
       "    echo 'OpenBao unsealed successfully!'\n"
       "    \n"
       "elif [ \"$SEALED\" = \"true\" ]; then\n"
       "    echo '⚠OpenBao is initialized but sealed'\n"
       "    \n"
       "    if [ -f \"/tmp/openbao-unseal-key\" ]; then\n"
       "        echo 'Attempting to unseal with existing key...'\n"
       "        UNSEAL_KEY=$(cat /tmp/openbao-unseal-key)\n"
       "        \n"
       "        curl -s -X POST \"$BAO_ADDR/v1/sys/unseal\" \\\n"
       "            -H 'Content-Type: application/json' \\\n"
       "            -d \"{\\\"key\\\":\\\"$UNSEAL_KEY\\\"}\"\n"
       "        \n"
       "        echo 'OpenBao unsealed with existing key!'\n"
       "    else\n"
       "        echo 'OpenBao is sealed but no unseal key found'\n"
       "        echo '   Manual intervention required'\n"
       "        exit 1\n"
       "    fi\n"
       "else\n"
       "    echo 'OpenBao is already initialized and unsealed!'\n"
       "    \n"
       "    # Ensure we have the root token available\n"
       "    if [ ! -f \"/tmp/openbao-root-token\" ]; then\n"
       "        echo 'Root token not found locally. OpenBao is ready but you may need to provide the root token manually.'\n"
       "    fi\n"
       "fi\n\n"
       "# Final verification\n"
       "echo 'Final status verification...'\n"
       "FINAL_STATUS=$(curl -s \"$BAO_ADDR/v1/sys/health\")\n"
       "FINAL_SEALED=$(echo \"$FINAL_STATUS\" | jq -r '.sealed')\n"
       "FINAL_INITIALIZED=$(echo \"$FINAL_STATUS\" | jq -r '.initialized')\n\n"
       "if [ \"$FINAL_SEALED\" = \"false\" ] && [ \"$FINAL_INITIALIZED\" = \"true\" ]; then\n"
       "    echo 'OpenBao is fully ready!'\n"
       "    echo 'Address: http://127.0.0.1:8200'\n"
       "    \n"
       "    if [ -f \"/tmp/openbao-root-token\" ]; then\n"
       "        echo 'Root token: Available at /tmp/openbao-root-token'\n"
       "    fi\n"
       "else\n"
       "    echo 'OpenBao is not in the expected ready state'\n"
       "    echo \"Final status: $FINAL_STATUS\"\n"
       "    exit 1\n"
       "fi\n"))

(defn- create-setup-secrets-script [namespace]
  "Script to set up initial secrets after OpenBao is ready"
  (str "#!/bin/bash\n"
       "set -e\n\n"
       "NAMESPACE=\"" namespace "\"\n"
       "BAO_ADDR='http://127.0.0.1:8200'\n"
       "PID_FILE=\"/tmp/openbao-setup-pf.pid\"\n\n"
       "if [ ! -f \"/tmp/openbao-root-token\" ]; then\n"
       "    echo 'Root token not found. Cannot set up secrets.'\n"
       "    exit 1\n"
       "fi\n\n"
       "ROOT_TOKEN=$(cat /tmp/openbao-root-token)\n\n"
       "# Cleanup function\n"
       "cleanup() {\n"
       "    if [ -f \"$PID_FILE\" ]; then\n"
       "        PID=$(cat \"$PID_FILE\")\n"
       "        kill $PID 2>/dev/null || true\n"
       "        rm -f \"$PID_FILE\"\n"
       "    fi\n"
       "}\n"
       "trap cleanup EXIT\n\n"
       "# Start port-forward\n"
       "echo 'Starting port-forward for secrets setup...'\n"
       "kubectl port-forward -n \"$NAMESPACE\" svc/openbao 8200:8200 > /tmp/setup-pf.log 2>&1 &\n"
       "echo $! > \"$PID_FILE\"\n\n"
       "# Wait for port-forward\n"
       "for i in {1..15}; do\n"
       "    if curl -s --max-time 2 \"$BAO_ADDR/v1/sys/health\" >/dev/null 2>&1; then\n"
       "        break\n"
       "    fi\n"
       "    if [ $i -eq 15 ]; then\n"
       "        echo 'Port-forward failed for secrets setup'\n"
       "        exit 1\n"
       "    fi\n"
       "    sleep 2\n"
       "done\n\n"
       "echo 'Setting up OpenBao secrets...'\n\n"
       "# Enable KV secrets engine (ignore error if already exists)\n"
       "echo 'Enabling KV secrets engine...'\n"
       "curl -s -H \"X-Vault-Token: $ROOT_TOKEN\" \\\n"
       "    -X POST \"$BAO_ADDR/v1/sys/mounts/secret\" \\\n"
       "    -d '{\"type\":\"kv-v2\"}' || echo '   (KV engine may already exist)'\n\n"
       "echo 'OpenBao secrets setup complete!'\n"))

(defn deploy-vault
  "Deploy OpenBao via Helm chart with fully automated initialization."
  [provider]
  (let [core-v1 (.. k8s -core -v1)
        helm-v3 (.. k8s -helm -v3)

        vault-ns (new (.. core-v1 -Namespace)
                      "vault-ns"
                      (clj->js {:metadata {:name "vault"}})
                      (clj->js {:provider provider}))

        values-path (.join path js/__dirname ".." "resources" "openbao.yml")
        helm-values (-> values-path
                        (fs/readFileSync "utf8")
                        (yaml/load))

        chart (new (.. helm-v3 -Chart)
                   "openbao"
                   (clj->js {:chart "openbao"
                             :fetchOpts {:repo "https://openbao.github.io/openbao-helm"}
                             :namespace (.. vault-ns -metadata -name)
                             :skipAwait true
                             :values helm-values})
                   (clj->js {:provider provider
                             :dependsOn [vault-ns]}))

        wait-ready-command
        (new local/Command
             "openbao-wait-ready"
             (clj->js {:create (create-wait-for-ready-script "vault")
                       :environment (clj->js {:KUBECONFIG "./kubeconfig.yaml"})})
             (clj->js {:dependsOn [chart]}))

        init-command
        (new local/Command
             "openbao-init"
             (clj->js {:create (create-init-script "vault")
                       :environment (clj->js {:KUBECONFIG "./kubeconfig.yaml"})})
             (clj->js {:dependsOn [wait-ready-command]}))


        setup-secrets-command
        (new local/Command
             "openbao-setup-secrets"
             (clj->js {:create (create-setup-secrets-script "vault")
                       :environment (clj->js {:KUBECONFIG "./kubeconfig.yaml"})})
             (clj->js {:dependsOn [init-command]}))

        root-token-command
        (new local/Command
             "get-root-token"
             (clj->js {:create "cat /tmp/openbao-root-token 2>/dev/null || echo 'TOKEN_NOT_FOUND'"})
             (clj->js {:dependsOn [setup-secrets-command]}))]
                                 {
                                  :root-token (.-stdout root-token-command)
                                  :address "http://127.0.0.1:8200"
                                  }
                                 ))

(defn configure-vault-access
  "Configure Pulumi config with OpenBao credentials after deployment"
  [openbao-deployment]
  (let [config-command
        (new local/Command
             "configure-pulumi-vault"
             (clj->js {:create (.apply (aget openbao-deployment "root_token")
                                       (fn [token]
                                         (if (= token "TOKEN_NOT_FOUND")
                                           "echo 'Warning: Root token not available for Pulumi config'"
                                           (str "pulumi config set vault:address 'http://127.0.0.1:8200'\n"
                                                "pulumi config set --secret vault:token '" token "'\n"
                                                "echo 'Pulumi vault config updated successfully'"))))})
             (clj->js {:dependsOn [(aget openbao-deployment "setup_secrets")]}))]
    config-command))