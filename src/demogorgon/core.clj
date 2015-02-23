(ns demogorgon.core
  (:import [java.io File]
           [java.util Random])
  (:require [tachyon.core :as irc]
            [tachyon.hooks :as irc-hooks]
            [clj-stacktrace.repl :as stacktrace]
            [clojure.walk]
            [clojure.tools.logging :as log])
  (:use [demogorgon.config]
        [demogorgon.unicode :only (unicode-hook)]
        [demogorgon.web :only (start-web stop-web)]
        [demogorgon.nh :only (online-players-hook last-dump-hook whereis-hook nh-start nh-run nh-init nh-stop)]
        [clojure.tools.nrepl.server :only (start-server stop-server)])
  (:gen-class))

(defonce bot (atom nil))

(defn get-memory-info []
  (let [runtime (Runtime/getRuntime)
        total (int (/ (.totalMemory runtime) 1048576))
        free (int (/ (.freeMemory runtime) 1048576))
        max (int (/ (.maxMemory runtime) 1048576))
        used (- total free)]
    (str "used " used " free " free " total " total " max " max)))

(defn print-debug []
  (log/debug (str "before " (get-memory-info)))
  (.gc (Runtime/getRuntime))
  (log/debug (str "after  " (get-memory-info))))

(defn rand-hook [irc object match]
  (let [words (.split (second match) " ")
        random (Random.)
        idx (.nextInt random (alength words))
        word (aget words idx)]
    word))

(defn create []
  (let [irc (irc/create (:irc @config))]
    {:connection irc
     :nh (nh-init irc)
     :web (ref nil)}))

(defn start [bot]
  (let [server (start-server :port 7888)]
    (nh-start (:nh bot))
    (log/info (str "Server is: " server))
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
    (let [home (System/getProperty "demogorgon.home" "")
          config-file (File. (str home (File/separator)
                                  ".." (File/separator)
                                  "etc" (File/separator)
                                  "demogorgon.conf"))]
      (log/info (str "Checking for config file: " (.toString config-file)))
      (if (.exists config-file)
        (do
          (log/info "Found config file")
          (read-config config-file))))
    (swap! bot (fn [x] (create)))
    (start @bot)
    (catch Exception e
      (log/error e "Failed to start"))))
-
