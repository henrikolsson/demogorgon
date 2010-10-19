(ns demogorgon.core
  (:import [org.apache.log4j Logger])
  (:require [swank.swank :as swank])
  (:require [tachyon.core :as irc])
  (:require [tachyon.hooks :as irc-hooks])
  (:use [clj-stacktrace core repl]
        [demogorgon.config]
        [demogorgon.unicode :only (unicode-hook)]
        [demogorgon.web :only (start-web stop-web)]
        [demogorgon.nh :only (online-players-hook last-dump-hook nh-start nh-init nh-stop)])
  (:gen-class))

(def *connection* (irc/create (:irc config)))
(def *nh* (nh-init *connection*))
(def *web* (ref nil))

(defn -main[& args]
  (try 
   (let [logger (Logger/getLogger "main")]
     (swank/start-repl 4006)
     (nh-start *nh*)
     (irc-hooks/add-message-hook *connection* #"\.u ?(.*)?" #'unicode-hook)
     (irc-hooks/add-message-hook *connection* ".cur" #'online-players-hook)
     (irc-hooks/add-message-hook *connection* ".online" #'online-players-hook)
     (irc-hooks/add-message-hook *connection* #"\.last ?(.*)?" #'last-dump-hook)
     (irc-hooks/add-message-hook *connection* #"\.lastdump ?(.*)?" #'last-dump-hook)
     (irc-hooks/add-message-hook *connection* #"\.lasturl ?(.*)?" #'last-dump-hook)
     (irc/connect *connection*)
     (dosync
      (ref-set *web* (start-web))))
   (catch Exception e
     (pst e))))

