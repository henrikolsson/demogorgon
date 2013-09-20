(defproject demogorgon "1.1.0-SNAPSHOT"
  :description "demogorgon"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.apache.mina/mina-core  "2.0.0-RC1"]
                 [ring "1.0.2"]
                 [compojure "1.0.1"]
                 [org.clojure/java.jdbc "0.1.4"]
                 [org.slf4j/slf4j-log4j12 "1.6.0"]
                 [org.slf4j/slf4j-api "1.6.0"]
                 [clj-stacktrace "0.2.0"]
                 [tachyon "0.0.2-SNAPSHOT"]
                 [org.twitter4j/twitter4j-core "2.1.3"]
                 [org.xerial/sqlite-jdbc "3.7.2"]]
  :jvm-opts ["-Xmx256m"]
  :main demogorgon.core)
