(defproject demogorgon "1.1.6-SNAPSHOT"
  :description "demogorgon"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.2.371"]
                 [org.apache.mina/mina-core  "2.0.9"]
                 [ring "1.4.0"]
                 [compojure "1.4.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.slf4j/slf4j-log4j12 "1.7.12"]
                 [org.slf4j/slf4j-api "1.7.12"]
                 [clj-stacktrace "0.2.8"]
                 [tachyon "1.0.0-SNAPSHOT"]
                 [org.twitter4j/twitter4j-core "4.0.3"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [org.clojure/data.json "0.2.6"]
                 [postgresql/postgresql "9.3-1102.jdbc41"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.mchange/c3p0 "0.9.5"]
                 [org.flywaydb/flyway-core "3.2.1"]]
  :profiles
  {:uberjar {:aot :all}
   :start
   {:repl-options 
    {:init-ns demogorgon.core}}}
  :plugins [[lein-tar "3.2.0"]
            [org.clojars.cvillecsteele/lein-git-version "1.0.3"]]
  :tar {:uberjar true}
  :jvm-opts ["-Xmx128m"]
  :main demogorgon.core)

