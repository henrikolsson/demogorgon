(ns demogorgon.util
  (:use	[clojure.java.io :only (reader)]))

(defn read-lines [file]
  (line-seq (reader file)))

