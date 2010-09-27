(ns demogorgon.core
  (:import [org.apache.log4j Logger])
  (:require [swank.swank :as swank])
  (:require [tachyon.core :as irc])
  (:require [tachyon.hooks :as irc-hooks])
  (:use [clj-stacktrace core repl]
        [demogorgon.config]
        [demogorgon.unicode :only (unicode-hook)])
  (:gen-class))

(def *connection* (irc/create (:irc config)))
(defn -main[& args]
  (try 
   (let [logger (Logger/getLogger "main")]
     (swank/start-repl 4006)
     (irc-hooks/remove-message-hook *connection* #"\.u ?(.*)?" #'unicode-hook)
     (irc/connect *connection*))
   (catch Exception e
     (pst e))))
