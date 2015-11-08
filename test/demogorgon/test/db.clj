(ns demogorgon.test.db
  (:use [demogorgon.nh] :reload)
  (:use [demogorgon.db] :reload)
  (:use	[demogorgon.util])
  (:use	[demogorgon.config])
  (:use [clojure.test]))

(defn db-fixture [f]
  (let [config (get-resource "test.conf")]
    (read-config config))
  (migrate)
  (nh-init-db)
  (f))

(use-fixtures :once db-fixture)
 
(deftest can-get-latest-games
  (is (= (count (latest-games 3)) 3)))

