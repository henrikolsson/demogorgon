(defproject demogorgon "1.1.6-SNAPSHOT"
  :description "demogorgon"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.371"]
                 [org.apache.mina/mina-core  "2.0.9"]
                 [ring "1.4.0"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-defaults "0.1.4"]
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
                 [org.flywaydb/flyway-core "3.2.1"]
                 [org.clojure/clojurescript "1.7.122"]
                 [reagent "0.5.0"]
                 [cljs-ajax "0.5.1"]
                 [secretary "1.2.3"]]
  :source-paths ["src/clj" "src/cljs"]
  :profiles
  {:uberjar {:aot :all}
   :start
   {:repl-options 
    {:init-ns demogorgon.core}}}
  :plugins [[lein-tar "3.2.0"]
            [lein-figwheel "0.4.1"]
            [org.clojars.cvillecsteele/lein-git-version "1.0.3"]]
  :figwheel {:ring-handler demogorgon.web/app
             :nrepl-port 7888}
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :figwheel true
                        :compiler {:main "demogorgon.core"
                                   :asset-path "js/out"
                                   :output-to "resources/public/js/demogorgon.js"
                                   :output-dir "resources/public/js/out"}}]}
  :tar {:uberjar true}
  :jvm-opts ["-Xmx128m"]
  :main demogorgon.core)
