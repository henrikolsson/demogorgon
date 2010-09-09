(ns demogorgon.core
  (:import [org.apache.log4j Logger])
  (:require [swank.swank :as swank])
  (:require [demogorgon.irc :as irc])
  (:use [clj-stacktrace core repl]
        [demogorgon.config])
  (:gen-class))

(def *connection* (irc/create (:irc config)))

(defn bacon-hook [irc object match]
  (let [nick (:nick (:prefix object))]
    (if (.startsWith nick "kerio")
      "No bacon for you."
      "BACON!!")))

(defn -main[& args]
  (try 
   (let [logger (Logger/getLogger "main")]
     (swank/start-repl 4006)
     (irc/add-message-hook *connection* #" ?bacon" bacon-hook)
     (irc/connect *connection*))
   (catch Exception e
     (pst e))))
