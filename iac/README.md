## Infrastructure as Code using Pulumi in Clojurescript
My cluster configuration that serves to automate the deployment and handling of my services that I use for personal and public tasks. The goal initially of this is to both reduce my cost overhead (I was using vultr), improve reproducibility (we love Guix after all), increase stability as any change prior was changing a docker compose and potentially bringing services down on any mistakes (Caddy being the central funnel was a blessing and a curse), as well as improve security as our secrets and such can now be contained in OpenBao (Hashicorp Vault, but open source and maintained by the Linux Foundation).

I'll try to include any pertinent documentation here in the tooling I use or the setup.


#### Upcoming
Initially we'll try to migrate our services from a docker compose and into a reproducible and controlled deployment scheme here. I'll also likely break this into its own repo and instead reference it as a submodule in our dotfiles (because it makes far more sense that way).

I also would like to swap the Docker repo used to be a generated [Harbor](https://goharbor.io/) one (due to the time constraints this would add I'll cope).
For now with an .env file set up with your PAT token for [Dockerhub](https://hub.docker.com) and the repo set to `gigiaj/`
```
echo "$DOCKERHUB_PAT" | docker login --username gigiaj --password-stdin
```



#### Goals
The long term goal is for this to be a mostly uninteractive, to completion set up of my cloud services. Since it'll be IaC should I ever choose down the road to migrate certain ones to local nodes I run then that effort should also be more or less feasible.




### Initial requirements
#### Need to Revise as we swapped to using Pulumi Automation API so the entire process is automated

Pulumi and Node/NPM installed


Then we need to set up the Pulumi stack 


Then we can move to setting our handful of Pulumi initializing secrets (right now we just set for local)

If using hcloud then we need to get an API token from: https://console.hetzner.com/projects/<PROJECT-NUMBER-HERE>/security/tokens

Add that token to your .env file
```
export HCLOUD_TOKEN=<TOKENHERE>
```


If you don't have one you need to generate an SSH key.
We need to also enter our SSH public keys onto hcloud for simplicity sake: https://console.hetzner.com/projects/<PROJECT-NUMBER-HERE>/security/sshkeys

Add this to your .env file
```
export SSH_KEY_NAME=<NAME-OF-SSH-KEY-IN-HCLOUD>
```

Need to supply Pulumi the private key which can be grabbed something like 
```
echo "export PRIVATE_KEY=\"$(base64 -w 0 < ~/.ssh/id_ed25519)\"" >> .env
```
If you have any others you want to add, you can add them in the same way

Now you can do 
```
source .env

npm run deploy
```
Pulumi should be forced to set-up the stack and such due to the Automation API, so you can sit back and watch it be fully initialized.

As I add services over from my compose I'll detail the needs initialization needs.



### Vault

Vault will swap to using Wasabi S3 for the backend since it'll coordinate well with NAS auto-backups I already have configured for redundancy of the S3.
So Vault is only needed to be set-up once ever ideally. After the dummy values are updated and refreshed on the services you should be able to control and cycle through modifying Vault any secrets as needed.

To access the vault from your local -because opening it publicly would be a bad idea- you need to run:
```
kubectl --kubeconfig=kubeconfig.yaml port-forward -n vault vault-0 8200:8200
```
This enables us to access the openbao UI in our browser.
You can add secrets from this interface or if you want you can connect to the pod directly and run OpenBao CLI commands.


Deletion:
kubectl --kubeconfig=kubeconfig.yaml patch deployment nextcloud -n my-nextcloud -p '{"metadata":{"finalizers":[]}}' --type='merge'

kubectl --kubeconfig=kubeconfig.yaml patch statefulset nextcloud-redis-master -n my-nextcloud -p '{"metadata":{"finalizers":[]}}' --type='merge'

kubectl --kubeconfig=kubeconfig.yaml patch statefulset nextcloud-mariadb -n my-nextcloud -p '{"metadata":{"finalizers":[]}}' --type='merge'

kubectl --kubeconfig=kubeconfig.yaml patch statefulset nextcloud-redis-replicas -n my-nextcloud -p '{"metadata":{"finalizers":[]}}' --type='merge'

kubectl --kubeconfig=kubeconfig.yaml patch pvc nextcloud-nextcloud -n my-nextcloud -p '{"metadata":{"finalizers":[]}}' --type='merge'
