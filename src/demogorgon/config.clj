(ns demogorgon.config
  (:require [clojure.data.json :as json]))

(def config (atom
             {:un-dir "/opt/nethack.nu/var/unnethack/"
              :twitter {:consumer_key ""
                        :consumer_secret ""
                        :oauth_token ""
                        :oauth_token_secret ""}
              :irc {:nick "demogorgon"
                    :username "demogorgon"
                    :realname "demogorgon"
                    :servers [["irc.freenode.net" 6667] ["kornbluth.freenode.net" 6667]]
                    :channels ["#unnethack"]}}
             :db {:classname "org.sqlite.JDBC"
                  :subprotocol "sqlite"
                  :subname "/tmp/nh.db"
                  :create true}))

(defn read-config [f]
  (swap! config merge
         (clojure.walk/keywordize-keys
          (json/read-str (slurp f)))))

