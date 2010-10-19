(ns demogorgon.web
  (:use compojure.core, ring.adapter.jetty)
  (:require [compojure.route :as route]))

(defroutes main-routes
  (GET "/" [] "<h1>Hello World</h1>")
  (route/not-found "<h1>Page not found</h1>"))

(defn start-web []
  (run-jetty main-routes {:port 8080 :join false}))

(defn stop-web [j]
  (.stop j))
