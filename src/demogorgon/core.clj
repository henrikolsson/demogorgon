(ns demogorgon.core
  (:import [org.apache.log4j Logger]
           [java.util Random])
  (:require [tachyon.core :as irc])
  (:require [tachyon.hooks :as irc-hooks])
  (:require [clj-stacktrace.repl :as stacktrace]) 
  (:use [demogorgon.config]
        [demogorgon.unicode :only (unicode-hook)]
        [demogorgon.web :only (start-web stop-web)]
        [demogorgon.nh :only (online-players-hook last-dump-hook whereis-hook nh-start nh-init nh-stop)])
  (:gen-class))

(def logger (Logger/getLogger "demogorgon.core"))

(defn get-memory-info []
  (let [runtime (Runtime/getRuntime)
        total (int (/ (.totalMemory runtime) 1048576))
        free (int (/ (.freeMemory runtime) 1048576))
        max (int (/ (.maxMemory runtime) 1048576))
        used (- total free)]
    (str "used " used " free " free " total " total " max " max)))

(defn print-debug []
  (.debug logger (str "before " (get-memory-info)))
  (.gc (Runtime/getRuntime))
  (.debug logger (str "after  " (get-memory-info))))

(defn rand-hook [irc object match]
  (let [words (.split (second match) " ")
        random (Random.)
        idx (.nextInt random (alength words))
        word (aget words idx)]
    word))

(defn create []
  (let [irc (irc/create (:irc config))]
    {:connection irc
    :nh (nh-init irc)
    :web (ref nil)}))

(defn start [bot]
  (let [logger (Logger/getLogger "main")]
    (nh-start (:nh bot))
    
    (irc-hooks/add-message-hook (:connection bot) #"^\.rng (.+)" #'rand-hook)
    (irc-hooks/add-message-hook (:connection bot) #"^\.rnd (.+)" #'rand-hook)
    (irc-hooks/add-message-hook (:connection bot) #"^\.random (.+)" #'rand-hook)
    (irc-hooks/add-message-hook (:connection bot) ".debug" (fn [& rest] (get-memory-info)))
    (irc-hooks/add-message-hook (:connection bot) ".gc" (fn [& rest] (.gc (Runtime/getRuntime))))
    (irc-hooks/add-message-hook (:connection bot) #"^\.u ?(.*)?" #'unicode-hook)
    (irc-hooks/add-message-hook (:connection bot) ".cur" #'online-players-hook)
    (irc-hooks/add-message-hook (:connection bot) ".online" #'online-players-hook)
    (irc-hooks/add-message-hook (:connection bot) #"^\.last ?(.*)?" #'last-dump-hook)
    (irc-hooks/add-message-hook (:connection bot) #"^\.lastdump ?(.*)?" #'last-dump-hook)
    (irc-hooks/add-message-hook (:connection bot) #"^\.lasturl ?(.*)?" #'last-dump-hook)
    (irc-hooks/add-message-hook (:connection bot) #"^\.whereis ?(.*)?" #'whereis-hook)
    (irc/connect (:connection bot))
    (dosync
     (ref-set (:web bot) (start-web)))))

(defn -main[& args]
  (try
    (start (create))
    (catch Exception e
      (stacktrace/pst e))))
