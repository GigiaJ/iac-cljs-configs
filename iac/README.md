## Infrastructure as Code using Pulumi (will swap it to Clojurescript after I get it stable)
To preface, writing it initially in Clojurescript without an immaculate handle on Pulumi is not the best idea. Easier to do it in Javascript for the sake of having docs to reference.

My cluster configuration that serves to automate the deployment and handling of my services that I use for personal and public tasks. The goal initially of this is to both reduce my cost overhead (I was using vultr), improve reproducibility (we love Guix after all), increase stability as any change prior was changing a docker compose and potentially bringing services down on any mistakes (Caddy being the central funnel was a blessing and a curse), as well as improve security as our secrets and such can now be contained in OpenBao (Hashicorp Vault, but open source and maintained by the Linux Foundation).

I'll try to include any pertinent documentation here in the tooling I use or the setup.


#### Upcoming
Initially we'll try to migrate our services from a docker compose and into a reproducible and controlled deployment scheme here. I'll also likely break this into its own repo and instead reference it as a submodule in our dotfiles (because it makes far more sense that way).

Since hcloud keeps (seriously, several times) making me wait for verification I've opted to go ahead and rewrite it into Clojurescript.

#### Goals
The long term goal is for this to be a mostly uninteractive, to completion set up of my cloud services. Since it'll be IaC should I ever choose down the road to migrate certain ones to local nodes I run then that effort should also be more or less feasible.




### Vault
Vault set up requires doing this when it gets everything provisioned (you'll have to cancel the pulumi up)

Run this:
```pulumi stack output kubeconfig --show-secrets > kubeconfig.yaml  ```


```
kubectl --kubeconfig=kubeconfig.yaml exec -n vault -it vault-0 -- /bin/sh
```

Inside the new shell:
```
vault operator init

vault operator unseal <PASTE_UNSEAL_KEY_1>
vault operator unseal <PASTE_UNSEAL_KEY_2>
vault operator unseal <PASTE_UNSEAL_KEY_3>
```

Then you need to run:
```
kubectl --kubeconfig=kubeconfig.yaml port-forward -n vault vault-0 8200:8200
```
This enables us to access the openbao UI in our browser

Open a new terminal window (leave that one open as it establishes a connection to the vault)
Run the following:
```
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='<PASTE_YOUR_INITIAL_ROOT_TOKEN>'
```

Open another terminal and connect to the pod 
```
kubectl --kubeconfig=kubeconfig.yaml exec -it openbao-0 -n vault -- /bin/sh
```
then run:
```
# Set your token
export BAO_TOKEN='<PASTE_YOUR_INITIAL_ROOT_TOKEN>'
# Enables secrets
vault secrets enable -path=secret kv-v2
```
Just enables kv-v2 secrets engine

You can then do:
```
bao kv put secret/nextcloud adminPassword="..." dbPassword="..."
```

or just use the UI in your browser at 127.0.0.1:8200 since you're portforwarded to it
