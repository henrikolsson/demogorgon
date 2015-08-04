(defproject demogorgon "1.1.4-SNAPSHOT"
  :description "demogorgon"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.apache.mina/mina-core  "2.0.9"]
                 [ring "1.3.2"]
                 [compojure "1.3.2"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.slf4j/slf4j-log4j12 "1.7.10"]
                 [org.slf4j/slf4j-api "1.7.10"]
                 [clj-stacktrace "0.2.8"]
                 [tachyon "1.0.0-SNAPSHOT"]
                 [org.twitter4j/twitter4j-core "4.0.2"]
                 [org.xerial/sqlite-jdbc "3.8.7"]
                 [org.clojure/data.json "0.2.5"]
                 [postgresql/postgresql "9.3-1102.jdbc41"]
                 [org.clojure/tools.nrepl "0.2.7"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.mchange/c3p0 "0.9.2.1"]]
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

