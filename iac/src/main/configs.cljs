(ns configs)

(defn get-env [key default] (let [value (aget js/process.env key)]
                              (if (or (nil? value) (identical? value ""))
                                default value)))

(def cfg 
    {
   :sshKeyName (get-env "SSH_KEY_NAME" nil)
   :sshPersonalKeyName (get-env "PERSONAL_KEY_NAME" nil)
   :privateKeySsh (.toString (js/Buffer.from (get-env "PRIVATE_KEY" nil) "base64") "utf-8")
   :hcloudToken (get-env "HCLOUD_TOKEN" nil)
   }
  )