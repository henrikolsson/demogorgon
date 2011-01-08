(ns demogorgon.config)

(def config
     {:un-dir "/opt/nethack.nu/var/unnethack/"
      :twitter {:consumer_key "reEFD0AjpXtn7mJ6MVlX1g"
                :consumer_secret "Nij9GdGR58VRDR5dbTqQin97HnhgdXwogY6lnrKxSrs"
                :oauth_token "179042090-SNSfPwsE2EIKTAUPicBLKaccdbBkgoSt5y737FsY"
                :oauth_token_secret "SqskJ8PTPXfMCwMxwioJRcpHGGNutC7xsgfeBmR9rMU"}
      :irc {:nick "demogorgon"
            :username "demogorgon"
            :realname "demogorgon"
            :servers [["irc.freenode.net" 6667] ["kornbluth.freenode.net" 6667]]
            :channels ["#unnethack"]}})
