(ns generation-p.social
  (:require [generation-p.model :as m]
            [generation-p.image :as image]
            [twitter.oauth :as tw.oauth]
            [twitter.api.restful :as tw.api]
            [twitter.request :as tw.request]))

(def ^:const ^:private retweet-to-favorite-weighting 30)

(defn- is-prod? [] (= "prod" (System/getenv "ENV")))

;; via https://github.com/adamwynne/twitter-api
(def ^:private creds
  (tw.oauth/make-oauth-creds (System/getenv "CONSUMER_KEY")
                             (System/getenv "CONSUMER_SECRET")
                             (System/getenv "ACCESS_TOKEN")
                             (System/getenv "SECRET_KEY")))

(defn debut [individual]
  (if (not (is-prod?))
    (rand-int 100000)
    (let [filename   (image/save-individual-as-image individual)
          status-msg (format "gen %d. %s"
                             (::m/generation-num individual)
                             (::m/id individual))
          ;; note: the media-upload-chunked function in the docs isn't published
          status     (tw.api/statuses-update-with-media
                      :oauth-creds
                      creds
                      :body [(tw.request/file-body-part filename)
                             (tw.request/status-body-part status-msg)])]
      (-> status :body :id))))

(defn get-fitness [individual]
  (if (not (is-prod?))
    (rand-int 20)
    (let [status (-> (tw.api/statuses-show-id :oauth-creds
                                              creds
                                              :params {:id (::m/social-id individual)})
                     :body)]
      (+ (* retweet-to-favorite-weighting
            (:retweet_count status))
         (:favorite_count status)))))

(comment

  ;; DANGER!!!
  (defn- delete-status [individual]
    (tw.api/statuses-destroy-id :oauth-creds
                                creds
                                :params {:id (::m/social-id individual)}))

  (run! delete-status (m/get-generation 0))
  ;; DANGER!!!


  )
