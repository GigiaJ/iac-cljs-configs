(ns configs)

(defn get-env [key default] (let [value (aget js/process.env key)]
                              (if (or (nil? value) (identical? value ""))
                                default value)))

(def cfg
  {:sshKeyName (get-env "SSH_KEY_NAME" nil)
   :sshPersonalKeyName (get-env "PERSONAL_KEY_NAME" nil)
   :privateKeySsh (.toString (js/Buffer.from (get-env "PRIVATE_KEY" nil) "base64") "utf-8")
   :hcloudToken (get-env "HCLOUD_TOKEN" nil)
   :wasabiId (get-env "WASABI_ID" nil)
   :wasabiKey (get-env "WASABI_KEY" nil)
   
   :apiToken (get-env "CLOUDFLARE_TOKEN" nil)

   ;; Non-pulumi vals
   :resource-path (get-env "RESOURCE_PATH" "resources")

   :secrets-json (-> (js/require "path")
                     (.join js/__dirname ".." "init-secrets.json")
                     (js/require)
                     (js->clj :keywordize-keys true))
   :docker-repo (get-env "DOCKER_REPO" "")
   :dns-email (get-env "DNS_EMAIL" "")})