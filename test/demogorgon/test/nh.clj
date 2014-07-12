(ns demogorgon.test.nh
  (:use [demogorgon.nh] :reload)
  (:use	[demogorgon.util])
  (:use	[demogorgon.config])
  (:use [clojure.test]))

(deftest can-parse-line
  (is (=
       {:player "foobar" :turns "21269" :starttime "1279293021" :game_action "saved"}
       (parse-line "player=foobar:turns=21269:starttime=1279293021:game_action=saved"))))

(deftest can-init-db
  (let [config (get-resource "test.conf")]
    (read-config config))
  (nh-init-db))

