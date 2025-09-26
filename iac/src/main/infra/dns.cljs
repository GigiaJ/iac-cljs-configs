(ns dns
 (:require
  ["@pulumi/pulumi" :as pulumi]
  ["@pulumi/vault" :as vault]
  ["@pulumi/cloudflare" :as cloudflare]))

(defn setup-dns [zone-id vault-provider]
  (let [dns-secrets (.getSecret (.-generic vault)
                                (clj->js {:path "secret/dns-entries"})
                                (clj->js {:provider  vault-provider}))
        dns-entries-map (.-dataJson dns-secrets)
        ]
    (pulumi/all [dns-entries-map]
                (fn [[entries]]
                  (doall
                   (for [[name ip] entries]
                     (new cloudflare/Record (str "dns-record-" name)
                          (clj->js {:zoneId zone-id
                                    :name name
                                    :value ip
                                    :type "A" ;; Need to check the IP and determine if we should use AAAA or A
                                    :ttl 300}))))))))