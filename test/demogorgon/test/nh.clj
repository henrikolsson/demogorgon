(ns demogorgon.test.nh
  (:use [demogorgon.nh] :reload)
  (:use [clojure.test]))

(deftest can-parse-line
  (is (=
       {:player "foobar" :turns "21269" :starttime "1279293021" :game_action "saved"}
       (parse-line "player=foobar:turns=21269:starttime=1279293021:game_action=saved"))))
