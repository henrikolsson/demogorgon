(defproject demogorgon "1.0.0-SNAPSHOT"
  :description "demogorgon"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.apache.mina/mina-core  "2.0.0-RC1"]
                 [log4j/log4j "1.2.14"]
                 [ring/ring-jetty-adapter "0.2.5"]
                 [compojure "0.4.1"]
                 [hiccup "0.2.6"]
                 [sandbar "0.3.0-SNAPSHOT"]
                 [org.slf4j/slf4j-log4j12 "1.6.0"]
                 [org.slf4j/slf4j-api "1.6.0"]
                 [swank-clojure "1.3.0-SNAPSHOT"]
                 [clj-stacktrace "0.2.0"]]
  :dev-dependencies [[swank-clojure "1.3.0-SNAPSHOT"]]
  :main demogorgon.core)
