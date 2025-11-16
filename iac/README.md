## Infrastructure as Code using Pulumi in Clojurescript
My cluster configuration that serves to automate the deployment and handling of my services that I use for personal and public tasks. The goal initially of this is to both reduce my cost overhead (I was using vultr), improve reproducibility (we love Guix after all), increase stability as any change prior was changing a docker compose and potentially bringing services down on any mistakes (Caddy being the central funnel was a blessing and a curse), as well as improve security as our secrets and such can now be contained in OpenBao (Hashicorp Vault, but open source and maintained by the Linux Foundation).

I'll try to include any pertinent documentation here in the tooling I use or the setup.


#### Upcoming
Break this into three repos. IaC, the Pulumi CLJS library, and my dot files. We'll also be moving data from our old instances to the new IaC managed cluster. c:
Current roadmap for that is breaking apart the Vault provider into its actual core components as it is currently an anti-pattern in the way it combines multiple provider functionalities through it. It relying on the config file even is a bit of an issue.
Furthermore, we are unable to effectively destructure secrets in the execute function in the current design. However, since we'd want to change to remove the anti-pattern mentioned above, we'd ideally actually just reference secrets through the vault resource output from the given resource config's stack execution.

get-provider-outputs-config inside utils.providers.cljs currently runs under the expectation that shared stack already exists... which inherently is flawed on an initial run. Will need to revise a little. Similarly get-stack-refs works on the same flawed principle.
Maybe we can move them to stack definitions (which currently exist in base.cljs). I think in an ideal design we could actually inherently scope the entire thing out. I'm inspired by how Guix allows system definitions to be written, and there isn't anything stopping a large block for a stack being like:
```
(def some-stack
{:stack-registry
 [{:stack [:k8s:secret :k8s:chart]
   :app-namespace "kube-system"
   :app-name "hcloud-csi"
   :vault-load-yaml false
   :k8s:secret-opts {:metadata {:name "hcloud"
                            :namespace "kube-system"}
                 :stringData {:token  (-> cfg :hcloudToken)}}
   :k8s:chart-opts {:fetchOpts {:repo "https://charts.hetzner.cloud"}
                :values {:controller {:enabled false
                                      :existingSecret {:name "hcloud-csi-secret"}
                                      :node {:existingSecret {:name "hcloud-csi-secret"}}}}}}
    {:stack [:vault:prepare :docker:image :k8s:secret :k8s:chart]
   :app-namespace "caddy-system"
   :app-name      "caddy-ingress-controller"
   :k8s:image-port 8080
   :k8s:vault-load-yaml false
   :k8s:image-opts {:imageName '(str repo "/" app-name ":latest")}
   :docker:image-opts {:registry {:server (-> cfg :public-image-registry-url)
                                  :username (-> cfg :public-image-registry-username)
                                  :password (-> cfg :public-image-registry-password)}
                       :tags [(str (-> cfg :public-image-registry-url) "/" (-> cfg :public-image-registry-username) "/" "caddy")]
                       :push true}
   :k8s:chart-opts {:fetchOpts {:repo "https://caddyserver.github.io/ingress"}
                    :values
                    {:ingressController
                     {:deployment {:kind "DaemonSet"}
                      :daemonSet {:useHostPort true}
                      :ports {:web {:hostPort 80}
                              :websecure {:hostPort 443}}
                      :service {:type "NodePort"
                                :externalTrafficPolicy "Local"}
                      :image {:repository 'repo
                              :tag "latest"}
                      :config {:email 'email}}}}}
    ]
    :stack-references { :init (new pulumi/StackReference "init") 
                        :shared (new pulumi/StackReference "shared")}
    :provider-configs {:harbor {:stack :shared
                :outputs ["username" "password" "url"]}}
    })
```
and that effectively defines an entire stack and is executable on (with the option to scope out to files to reduce the sheer verbosity in a single)
In that regard, I think we've made decent headway in achieving a similar design and behavior where a config should provide reproducible results.
Due to the nature of npm packages, it is a bit hard to *lock* to certain package versions as easily.

DNS should be swapped with a Cloudflare provider instead and more appropriately allow EACH service to plainly define a DNS entry.

Local config loading or something should also be a provider, as obviously we would want to be able to pass through virtually anything to a service. That way they can be accessed later (this would replace the weird load-yaml that is a leftover from prior iterations)

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
kubectl get secret <SECRET-RESOURCE-NAME> -n harbor -o jsonpath='{.data}' | jq 'map_values(@base64d)'
-----
kubectl get secret harbor-creds-secrets -n harbor -o jsonpath='{.data}' | jq 'map_values(@base64d)'


Generating an RSA PKCS#1 key with openssl:
```
openssl genrsa -traditional -out core-token-pkcs1.key 2048
```

Convert to a single line
```
awk '{printf "%s\\n", $0}' core-token-pkcs1.key
```

Make a cert
```
openssl req -new -x509 -key core-token-pkcs1.key -out core-token.crt -days 365 -subj "/CN=harbor-core"
```

Convert to a single line
```
awk '{printf "%s\\n", $0}' core-token.crt
```

Hashing the htpassword
```
npm install bcryptjs
node -e 'console.log("admin:" + require("bcryptjs").hashSync("password", 10))'
```


https://www.pulumi.com/registry/packages/docker-build/api-docs/image/
https://www.pulumi.com/registry/packages/docker/api-docs/buildxbuilder/#create

