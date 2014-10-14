(ns demogorgon.watch
  (:import [org.apache.log4j Logger])
  (:import [java.nio.file FileSystems StandardWatchEventKinds]
           [java.io File])
  (:require [clojure.core.async :as async :refer [close! go chan put! <!]]))

(def ^{:private true} logger (Logger/getLogger "demogorgon.watch"))

(defn- to-file-collection [obj]
  (map #(File. %1)
       (if (coll? obj)
         obj
         [obj])))

(defn- get-subdirectories [path]
  (filter #(.isDirectory %) (file-seq path)))

(defn- java-event-to-keyword [e]
  (.trace logger e)
  (cond
   (= e StandardWatchEventKinds/ENTRY_MODIFY) :modify
   (= e StandardWatchEventKinds/ENTRY_CREATE) :create
   (= e StandardWatchEventKinds/ENTRY_DELETE) :delete))

(defn- run-watcher [ws channel keys]
  (try
    (.info logger "watcher starting")
    (while true
      (.trace logger "waiting for events..")
      (let [key (.take ws)]
        (doseq [event (.pollEvents key)]
          (.trace logger "got event")
          (put! channel [(java-event-to-keyword (.kind event))
                         (.resolve (get keys key) (.context event))])
          (.trace logger "dispatched"))
        (.reset key)))
    (catch java.nio.file.ClosedWatchServiceException cwse
      (.info logger "closed"))
    (finally
      (.info logger "done"))))

(defn create-watcher
  ([paths] (create-watcher paths {:recursive false}))
  ([paths options]
     (let [paths (to-file-collection paths)
           paths (if (:recursive options)
                   (concat paths
                           (flatten (map get-subdirectories paths)))
                   paths)
           paths (map #(.toPath %1) paths)
           watch-service (.newWatchService (FileSystems/getDefault))
           events (into-array [StandardWatchEventKinds/ENTRY_MODIFY
                               StandardWatchEventKinds/ENTRY_DELETE
                               StandardWatchEventKinds/ENTRY_CREATE])
           channel (chan)
           keys (into {} (map #(vector (.register %1 watch-service events) %1)
                              paths))]
       {:watch-service watch-service
        :channel channel
        :thread (Thread. (proxy [Runnable] []
                           (run []
                             (run-watcher watch-service channel keys))))})))

(defn start-watcher [watcher]
  (.start (:thread watcher))
  (:channel watcher))

(defn stop-watcher [watcher]
  (.close (:watch-service watcher))
  (close! (:channel watcher)))

