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

Plan to give OpenBao a single set of Wasabi IAM creds. Then OpenBao can be used to manage users and perms. Then we can make a role inside Bao.

I plan to start by giving OpenBao a single set of Wasabi IAM credentials. This will be the only time I need to do that. Once Bao has those credentials, it will be able to manage users and permissions for me automatically. For example, I might call it project alpha. I will link that role to a Wasabi policy that defines what kind of access it should have, like permission to read and write to buckets that start with project alpha.

That way when I need to Ican authenticate with Bao and ask for credentials tied to that role. Bao can then create a new IAM user in Wasabi, attach the correct policy, and generate temporary access keys. These keys are short-lived. This way we access storage, and once the time is up, Bao will automatically revoke the credentials and delete the user.
This setup will give more secure, on demand access to Wasabi without having to manage IAM users or worry about long term credentials.

Also the chart-opts in K8s can be simplified as we don't need to do the odd-wrapping we are currently doing and instead can provide the ability to pull secrets from the opts declaration themselves by making them a function that needs to resolve first.

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
kubectl port-forward -n vault openbao-0 8200:8200
```
This enables us to access the openbao UI in our browser.
You can add secrets from this interface or if you want you can connect to the pod directly and run OpenBao CLI commands.


Deletion:
kubectl --kubeconfig=kubeconfig.yaml patch deployment nextcloud -n nextcloud -p '{"metadata":{"finalizers":[]}}' --type='merge'

### Adding Services
Depending on the implementation there are a few steps needed, but usually much will be shared between helm charts and service/deployment declarations:
An excellent example of a new service in `src/main/k8s/services/mesite/service.cljs`
```
(ns k8s.services.mesite.service
  (:require
   [utils.k8s :as k8s-utils]
   [configs :refer [cfg]]))

(defn deploy [provider vault-provider]
  (k8s-utils/deploy-stack
   :namespace :vault-secrets :deployment :service :ingress
   {:provider provider
    :vault-provider vault-provider
    :app-namespace "generic"
    :app-name "mesite"
    :image-port 80
    :image (str (-> cfg :docker-repo) "/mesite:latest")}))
```
Then inside deployments.cljs you simply need to add to the app-list function:
```
(defn app-list [config provider kc]
  (let [stack-ref (new pulumi/StackReference "cluster")
        vault-provider (new vault/Provider
                            "vault-provider"
                            (clj->js {:address (.getOutput stack-ref "vaultAddress")
                                      :token   (.getOutput stack-ref "vaultToken")})) 
        cloudflare-result (dns/setup-dns config vault-provider)
        mesite-result (mesite-service/deploy provider vault-provider)
        ]
    {
     :cloudflare cloudflare-result}))
```

--- Helpful tips and commands ---

Something helpful with S3proxy is to use it locally and set up how you *need* to connect to the S3 provider of your choice. Combo this with a lightweight command line tool like s3cmd. The output of this will be the contents within the bucket name provided.

```
docker run -d -p 8081:80 --name s3proxy --env S3PROXY_ENDPOINT=http://0.0.0.0:80 --env S3PROXY_AUTHORIZATION=none --env JCLOUDS_PROVIDER=s3 --env JCLOUDS_IDENTITY=YOUR_SECRET_ID --env JCLOUDS_CREDENTIAL=YOUR_SECRET_KEY_HERE --env JCLOUDS_ENDPOINT=PROVIDER_ENDPOINT_HERE --env JCLOUDS_REGION=us-east-1 andrewgaul/s3proxy

s3cmd --access_key="something" --secret_key="something" --host=localhost:8081 --host-bucket="" --no-ssl ls s3://BUCKET_NAME_HERE
```


Setting up IAM (if the provider supports it) for the individual bucket is a generally good idea to prevent any overreach for permissions.
Here is a sample policy that more or less grants free reign to a SINGLE bucket:
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllowS3CSIDriver",
            "Effect": "Allow",
            "Action": [
                "s3:CreateBucket",
                "s3:DeleteBucket",
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:ListBucket",
                "s3:ListBucketMultipartUploads",
                "s3:AbortMultipartUpload"
            ],
            "Resource": [
                "arn:aws:s3:::BUCKET_NAME",
                "arn:aws:s3:::BUCKET_NAME/*"
            ]
        }
    ]
}
```
You can then, of course, make that policy applied to users or a group however you wish.




To check out the secrets inside a Kubernetes secrets resource you can use the following which combos JQ to parse the output:
```
kubectl get secret <secrets-name> -n <namespace> -o jsonpath='{.data}' | jq 'map_values(@base64d)'
```
kubectl get secret harbor-core -n harbor -o jsonpath='{.data}' | jq 'map_values(@base64d)'
-----

