(ns demogorgon.test.unicode
  (:use [demogorgon.unicode] :reload)
  (:use [clojure.test]))

(deftest can-lookupunicode
  (is (= (unicode-lookup "snowman without snow") '({:name "SNOWMAN WITHOUT SNOW", :codepoint "26C4"}))))

;; (deftest replace-me
;;   (is false "No tests have been written."))
