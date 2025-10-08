(ns infra.dns
  (:require
   [clojure.string :as str]
   [utils.vault :as utils]
   ["@pulumi/pulumi" :as pulumi]
   ["@pulumi/kubernetes" :as k8s]
   ["@pulumi/vault" :as vault]
   ["@pulumi/cloudflare" :as cloudflare]
   ["@pulumi/command/local" :as local]))

(defn get-record-type
  "Determines the DNS record type (A or AAAA) based on IP address format."
  [ip-address]
  (if (.includes ip-address ":")
    "AAAA"
    "A"))

(defn- get-node-ips [] 
  (str "kubectl get nodes -o jsonpath='{range .items[*]}{.status.addresses[?(@.type==\"ExternalIP\")].address}{\"\\n\"}{end}'"))

(defn setup-dns [{:keys [vault-provider pulumi-cfg]}]
  (let [get-node-ips (local/Command.
                      "get-node-ips"
                      (clj->js {:create (get-node-ips)
                                :environment {:KUBECONFIG "./kubeconfig.yaml"}}))
        token (.requireSecret pulumi-cfg "apiToken")
        cloudflare-provider (new cloudflare/Provider "cloudflare-provider"
                                 (clj->js {:apiToken token}))
        service-name "dns"
        vault-path (str "secret/" service-name)
        initialize (utils/initialize-mount vault-provider vault-path service-name)
        dns-configs-secret (.getSecret (.-generic vault)
                                       (clj->js {:path vault-path})
                                       (clj->js {:provider  vault-provider
                                                 :dependsOn initialize}))


        node-ips-output (.-stdout get-node-ips)]

    (.apply node-ips-output
            (fn [command-output]
              (let [node-ips (-> command-output
                                 str/split-lines
                                 (->> (map #(first (str/split % #" ")))
                                      (filter seq)))]
                (.then dns-configs-secret
                       (fn [secret-data]
                         (let [hostname-to-zone (-> (.-data secret-data)
                                                    (js->clj :keywordize-keys true))]
                           (vec
                            (for [[hostname zone-id] hostname-to-zone
                                  [index ip] (map-indexed vector node-ips)
                                  :when (and hostname zone-id ip)]
                              (new cloudflare/DnsRecord
                                   (str "dns-" (name hostname) "-node-" index)
                                   (clj->js {:zoneId zone-id
                                             :name hostname
                                             :content ip
                                             :type (get-record-type ip)
                                             :ttl 300})
                                   (clj->js {:provider cloudflare-provider}))))))))))))

(def config
  {:stack [:execute]
   :app-name "dns"
   :app-namespace "dns"
   :exec-fn setup-dns})


