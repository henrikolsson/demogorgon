(ns demogorgon.util
  (:use	[clojure.java.io :only (reader)]))

(defn get-resource [name]
  (.getResourceAsStream (.getContextClassLoader (Thread/currentThread)) name))

(defn read-lines [file]
  (line-seq (reader file)))

