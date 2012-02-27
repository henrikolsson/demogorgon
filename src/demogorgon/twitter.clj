(ns demogorgon.twitter
  (:import [java.net URL]
           [twitter4j TwitterFactory]
           [twitter4j.http AccessToken])
  (:require [clj-stacktrace.repl :as stacktrace])
  (:use	[demogorgon.config]
        [clojure.java.io :only (reader)]))

(defn shorten-url [url]
    (let [url (new URL (str "http://is.gd/api.php?longurl=" url))
          connection (.openConnection url)
          r (reader (.getInputStream connection))]
      (.readLine r)))

(defn tweet [message]
  (let [factory (new TwitterFactory)
        twitter (.getInstance factory)
        accessToken (new AccessToken
                         (:oauth_token (:twitter config))
                         (:oauth_token_secret (:twitter config)))]
    (doto twitter
      (.setOAuthConsumer (:consumer_key (:twitter config))
                         (:consumer_secret (:twitter config)))
      (.setOAuthAccessToken accessToken)
      (.updateStatus message))))

