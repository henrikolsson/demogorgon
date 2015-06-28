(ns demogorgon.db
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:use [demogorgon.config])
  (:require [clojure.java.jdbc :as sql]))

(defn pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               (.setMaxIdleTimeExcessConnections (* 30 60))
               (.setMaxIdleTime (* 3 60 60)))]
        {:datasource cpds}))

(def pooled-db (delay (pool (:db @config))))

(defn latest-games
  ([] (latest-games 25))
  ([limit]
   (sql/query @pooled-db ["select * from xlogfile order by endtime desc limit ?" limit])))

