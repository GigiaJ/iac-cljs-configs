(ns k8s.services.renovate.service)

;https://docs.renovatebot.com/self-hosted-configuration/
(def config
  {:stack [:vault-secrets :chart :cronjob]
   :app-namespace "renovate"
   :app-name      "renovate"
   :image-port    8080
   :vault-load-yaml true
   :chart-opts
   {:fetchOpts {:repo "https://docs.renovatebot.com/helm-charts"}
    :values
    {:renovate
     {:config {:platform "github"
               :token    "vault:renovate/github-token"
               :logLevel "info"
               :repositories ["your-org/your-repo"]
               :onboardingConfig {:extends ["config:base"]}}}}
    :transformations
    (fn [args _opts]
      (let [kind (get-in args [:resource :kind])]
        (if (= kind "CronJob")
          (update-in args [:resource :spec :jobTemplate :spec :template :metadata :annotations]
                     #(assoc (or % {}) "pulumi.com/skipAwait" "true"))
          args)))}})
