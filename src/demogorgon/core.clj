(ns demogorgon.core
  (:import [org.apache.log4j Logger]
           [java.util Random])
  (:require [tachyon.core :as irc])
  (:require [tachyon.hooks :as irc-hooks])
  (:use [clj-stacktrace core repl]
        [demogorgon.config]
        [demogorgon.unicode :only (unicode-hook)]
        [demogorgon.web :only (start-web stop-web)]
        [demogorgon.nh :only (online-players-hook last-dump-hook whereis-hook nh-start nh-init nh-stop)])
  (:gen-class))

(def logger (Logger/getLogger "demogorgon.core"))
(def *connection* (irc/create (:irc config)))
(def *nh* (nh-init *connection*))
(def *web* (ref nil))

(defn get-memory-info []
  (let [runtime (Runtime/getRuntime)
        total (int (/ (.totalMemory runtime) 1048576))
        free (int (/ (.freeMemory runtime) 1048576))
        max (int (/ (.maxMemory runtime) 1048576))
        used (- total free)]
    (str "used " used " free " free " total " total " max " max)))

(defn print-debug []
  (let [thread (:thread @*nh*)]
    (.debug logger (str (.getName thread) ": " (.getState thread))))
  (.debug logger (str "before " (get-memory-info)))
  (.gc (Runtime/getRuntime))
  (.debug logger (str "after  " (get-memory-info))))

(defn rand-hook [irc object match]
  (let [words (.split (second match) " ")
        random (Random.)
        idx (.nextInt random (alength words))
        word (aget words idx)]
    word))

(defn -main[& args]
  (try 
   (let [logger (Logger/getLogger "main")]
     (nh-start *nh*)
     
     (irc-hooks/add-message-hook *connection* #"^\.rng (.+)" #'rand-hook)
     (irc-hooks/add-message-hook *connection* #"^\.rnd (.+)" #'rand-hook)
     (irc-hooks/add-message-hook *connection* #"^\.random (.+)" #'rand-hook)
     (irc-hooks/add-message-hook *connection* ".debug" (fn [& rest] (get-memory-info)))
     (irc-hooks/add-message-hook *connection* ".gc" (fn [& rest] (.gc (Runtime/getRuntime))))
     (irc-hooks/add-message-hook *connection* #"^\.u ?(.*)?" #'unicode-hook)
     (irc-hooks/add-message-hook *connection* ".cur" #'online-players-hook)
     (irc-hooks/add-message-hook *connection* ".online" #'online-players-hook)
     (irc-hooks/add-message-hook *connection* #"^\.last ?(.*)?" #'last-dump-hook)
     (irc-hooks/add-message-hook *connection* #"^\.lastdump ?(.*)?" #'last-dump-hook)
     (irc-hooks/add-message-hook *connection* #"^\.lasturl ?(.*)?" #'last-dump-hook)
     (irc-hooks/add-message-hook *connection* #"^\.whereis ?(.*)?" #'whereis-hook)
     (irc/connect *connection*)
     (dosync
      (ref-set *web* (start-web))))
   (catch Exception e
     (pst e))))
