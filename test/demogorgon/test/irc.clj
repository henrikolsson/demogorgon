(ns demogorgon.test.irc
  (:use [demogorgon.irc] :reload)
  (:use [clojure.test]))

(deftest can-parse-line
  (is (= {:prefix nil
          :command "TEST"
          :args ["a" "b"]} (parse-line "TEST a b")))
  (is (= {:prefix "test.example.com"
          :command "372"
          :args ["foo bar"]} (parse-line ":test.example.com 372 :foo bar")))
  (is (= {:prefix "someone!foo@example.com"
          :command "PRIVMSG"
          :args ["me" "hey you"]} (parse-line ":someone!foo@example.com PRIVMSG me :hey you")))
  (is (= {:prefix "someone!foo@example.com"
          :command "PRIVMSG"
          :args ["me" "hey you"]} (parse-line ":someone!foo@example.com PRIVMSG me :hey you"))))



