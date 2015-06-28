(ns demogorgon.test.db
  (:use [demogorgon.db] :reload)
  (:use	[demogorgon.util])
  (:use	[demogorgon.config])
  (:use [clojure.test]))

(deftest can-get-latest-games
  (let [config (get-resource "test.conf")]
    (read-config config))
  (is (= (count (latest-games 3)) 3)))

