(ns demogorgon.core
  (:import [java.io File]
           [java.util Random])
  (:require [tachyon.core :as irc]
            [clj-stacktrace.repl :as stacktrace]
            [clojure.walk]
            [clojure.tools.logging :as log])
  (:use [demogorgon.config]
        [demogorgon.unicode :only (unicode-hook)]
        [demogorgon.web :only (start-web stop-web)]
        [demogorgon.nh :only (online-players-hook last-dump-hook whereis-hook nh-start nh-run nh-init nh-stop)]
        [clojure.tools.nrepl.server :only (start-server stop-server)]
        [clojure.core.async :refer [chan close! <! >! pub sub unsub go go-loop timeout]])
  (:gen-class))

(defonce bot (atom nil))

(defn get-memory-info [& args]
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
     :web (start-web)
     :repl (start-server :port 7888)}))

(defn get-reply-target [data]
  (if (.startsWith (:target data) "#")
    (:target data)
    (get-in data [:prefix :nick])))

(def msg-handlers [[[#"^\.last ?(.*)?" #"^\.lastdump ?(.*)?"] #'last-dump-hook]
               [[#"^\.rng ?(.*)?" #"^\.rand ?(.*)?"] #'rand-hook]
               [[#"^\.cur" #"^\.online"] #'online-players-hook]
               [[#"^\.last ?(.*)?" #"^\.lastdump ?(.*)?" #"^\.lasturl ?(.*)?"] #'last-dump-hook]
               [[#"^\.debug"] #'get-memory-info]])

(defn handle-privmsg [con data]
  (let [target (get-reply-target data)
        message (:msg data)
        words (seq (.split message " "))
        handler (first (filter
                        (fn [handler]
                          (some (fn [re]
                                  (re-find re message)) (first handler)))
                        msg-handlers))]
    (if handler
      (let [match (some (fn [re]
                          (re-find re message)) (first handler))
            result ((second handler) con data match)]
        (if (seq? result)
          (doseq [l result]
            (irc/send-message con target l))
          (irc/send-message con target result))))))

(defn start [bot]
  (let [publication (irc/get-publication (:connection bot))]
    (let [subscriber (chan)]
      (sub publication :privmsg subscriber)
      (go-loop []
        (let [val (<! subscriber)]
          (if val
            (do (try
                  (handle-privmsg (:connection bot) val)
                  (catch Exception e
                    (log/error e "error in listener")))
                (recur))))))
    (log/info "Connecting to irc...")
    (irc/connect (:connection bot))))

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
    (reset! bot (create))
    (nh-start (:nh @bot))
    (start @bot)
    (catch Exception e
      (log/error e "Failed to start"))))

(defn shutdown [bot]
  (stop-server (:repl bot))  
  (stop-web (:web bot))
  (nh-stop (:nh bot))
  (irc/shutdown (:connection bot)))
