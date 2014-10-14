(defproject demogorgon "1.1.2-SNAPSHOT"
  :description "demogorgon"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.apache.mina/mina-core  "2.0.7"]
                 [ring "1.2.0"]
                 [compojure "1.1.5"]
                 [org.clojure/java.jdbc "0.3.4"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [clj-stacktrace "0.2.6"]
                 [tachyon "0.0.2"]
                 [org.twitter4j/twitter4j-core "3.0.3"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.clojure/data.json "0.2.5"]
                 [postgresql/postgresql "9.1-901-1.jdbc4"]
                 [org.clojure/tools.nrepl "0.2.3"]]

  :profiles
  {:start
   {:repl-options 
    {:init-ns demogorgon.core
     :init (demogorgon.core/-main)}}}
  :plugins [[lein-tar "3.2.0"]]
  :tar {:uberjar true} 
  :jvm-opts ["-Xmx128m"]
  :main demogorgon.core)

