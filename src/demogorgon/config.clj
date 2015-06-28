(ns demogorgon.config
  (:require [clojure.data.json :as json]))

(def config (atom
             {:un-dir "data/"
              :twitter {:consumer_key ""
                        :consumer_secret ""
                        :oauth_token ""
                        :oauth_token_secret ""}
              :irc {:nick "devogorgon"
                    :username "devogorgon"
                    :realname "devogorgon"
                    :servers [["irc.du.se" 6667]]
                    :channels ["#origo"]}
              :db {:classname "org.sqlite.JDBC"
                   :subprotocol "sqlite"
                   :subname "/tmp/nh.db"
                   :create true}}))

(defn read-config [f]
  (swap! config merge
         (clojure.walk/keywordize-keys
          (json/read-str (slurp f)))))

