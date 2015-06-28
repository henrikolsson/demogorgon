(ns demogorgon.test.web
  (:use [demogorgon.web] :reload)
  (:use	[demogorgon.util])
  (:use	[demogorgon.config])
  (:use [clojure.test])
  (:use [clojure.tools.logging]))

(deftest can-call-actions
  (ascensions)
  (game 123)
  (highscores)
  (cause "test")
  (last-games)
  (frontpage)
  (user "test")
  (users)
  (causes))
