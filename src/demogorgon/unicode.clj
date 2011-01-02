(ns demogorgon.unicode
  (:use	[clojure.contrib.duck-streams :only (reader read-lines)]))

(defn get-resource [name]
  (.getResourceAsStream (.getContextClassLoader (Thread/currentThread)) name))

(defn unicode-matches? [needle name codepoint]
  (or (> (.indexOf (.toLowerCase name) needle) -1)
      (= needle (.toLowerCase codepoint))))
    
(defn unicode-lookup [_symbol]
  (let [symbol (.toLowerCase _symbol)]
    (let [matches
          (filter (fn [^String line]
                    (let [tokens (.split line "\t")
                          ^String name (.trim (aget tokens 0))
                          ^String codepoint (.trim (aget tokens 1))]
                      (unicode-matches? symbol name codepoint)))
                  (read-lines (get-resource "unicode.txt")))]
      (sort-by (fn [item]
                 (- (count (:name item)) (count symbol)))
               (map (fn [^String line]
                      (let [tokens (.split line "\t")
                            ^String name (.trim (aget tokens 0))
                            ^String codepoint (.trim (aget tokens 1))]
                        {:name name :codepoint codepoint})) matches)))))

(defn unicode-hook [irc object match]
  (if (= (second match) "")
    (str "usage: " (first match) " <symbol>")
    (if (< (count (second match)) 3)
      (str "minimum search length is 3")
      (let [result (take 5 (unicode-lookup (second match)))]
        (if (first result)
          
          (map (fn [foo]
                 (str "U+" (:codepoint foo) " " (:name foo)
                      " (" (String. (Character/toChars (Integer/valueOf (:codepoint foo) 16))) ")"))
               result)
          "No match")))))



