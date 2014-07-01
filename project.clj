(defproject demogorgon "1.1.2-SNAPSHOT"
  :description "demogorgon"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.apache.mina/mina-core  "2.0.7"]
                 [ring "1.2.0"]
                 [compojure "1.1.5"]
                 [org.clojure/java.jdbc "0.2.3"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [clj-stacktrace "0.2.6"]
                 [tachyon "0.0.2"]
                 [org.twitter4j/twitter4j-core "3.0.3"]
                 [org.xerial/sqlite-jdbc "3.7.2"]]
  :profiles
  {:start
   {:repl-options 
    {:init-ns demogorgon.core
     :init (demogorgon.core/-main)}}}
  :plugins [[lein-tar "3.2.0"]]
  :tar {:uberjar true} 
  :jvm-opts ["-Xmx128m"]
  :main demogorgon.core)
