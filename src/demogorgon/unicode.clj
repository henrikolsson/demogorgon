(ns demogorgon.unicode
  (:use	[clojure.contrib.duck-streams :only (reader)]))

(defn get-resource [name]
  (.getResourceAsStream (.getContextClassLoader (Thread/currentThread)) name))

(defn unicode-lookup [_symbol]
  (let [symbol (.toLowerCase _symbol)]
    (with-open [rdr (reader (get-resource "unicode.txt"))]
      (let [lines (line-seq rdr)]
        (some (fn [^String line]
                (let [tokens (.split line "\t")
                      ^String name (.trim (aget tokens 0))
                      ^String codepoint (.trim (aget tokens 1))]
                  (if (or (.startsWith (.toLowerCase name) symbol)
                          (= symbol (.toLowerCase codepoint)))
                    {:name name :codepoint codepoint}
                    nil))) lines)))))

(defn unicode-hook [irc object match]
  (if (= (second match) "")
    (str "usage: " (first match) " <symbol>")
    (let [result (unicode-lookup (second match))]
      (if result
        (str "U+" (:codepoint result) " " (:name result)
             " (" (char (Integer/valueOf (:codepoint result) 16)) ")")
        "No match"))))

