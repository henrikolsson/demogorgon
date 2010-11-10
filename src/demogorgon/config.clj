(ns demogorgon.config)

(def config
     {:un-dir "/opt/nethack.nu/var/unnethack/"
      :twitter {:consumer_key ""
                :consumer_secret ""
                :oauth_token ""
                :oauth_token_secret ""}
      :irc {:nick "clojgorgon"
            :username "clojgorgon"
            :realname "clojgorgon"
            :servers [["irc.freenode.net" 6667] ["kornbluth.freenode.net" 6667]]
            :channels ["#dem0"]}})
