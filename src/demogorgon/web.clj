(ns demogorgon.web
  (:import [java.io File]
           [org.apache.log4j Logger])
  (:use [compojure.core :only (defroutes GET)]
        [ring.adapter.jetty :only (run-jetty)]
        [ring.util.codec :only (url-encode url-decode)]
        [hiccup.core :only (html escape-html)]
        [hiccup.page-helpers :only (doctype link-to)])
  (:require [compojure.route :as route]
            [clojure.contrib.sql :as sql]))

(def logger (Logger/getLogger "demogorgon.web"))

(def db {:classname "org.sqlite.JDBC"
                    :subprotocol "sqlite"
                    :subname "/tmp/nh.db"
                    :create true})

(defn layout [content]
  (html
   (doctype :html5)
   [:html
    [:head
     [:title "un.nethack.nu"]
     [:style {:type "text/css"}
      "body { font-family: verdana; font-size: 9px; }"
      "table { width: 100%; font-size: 11px; }"
      "th{ color: black; text-shadow:1px 1px 1px #bbb; }"
      "tr:nth-child(odd) { color: black; background-color:#eee; }"
      "tr:nth-child(even) { color: black; background-color:#fff; }"
      "tr:hover { background-color: #ccc; }"]]
    [:body
     [:h1 "un.nethack.nu"]
     [:div#menu
      [:a {:href "/"} "last 25 games"] " | "
      [:a {:href "/highscores"} "highscores"] " | "
      [:a {:href "/users"} "users"] " | "
      [:a {:href "/causes"} "causes"]]
     [:br]
     content]]))

(defn page-not-found []
  "<h1>Page not found</h1>")

(defn make-dump-link [row]
  (let [base (str "/user/" (:name row) "/dumps/" (:name row ) "." (:endtime row))
        file-base (str "/srv/un.nethack.nu" base)
        extension (if (.exists (File. (str file-base ".txt.html")))
                    ".txt.html"
                    (if (.exists (File. (str file-base ".txt")))
                      ".txt"
                      nil))]
    (if extension
      [:a {:href (str base extension)} "dump #" (:id row)]
      [:div "No dump file for game " (:id row)])))


(defn rs-row-to-tr [row]
  [:tr
   [:td [:a {:href (str "/game/" (:id row))} (:id row)]]
   [:td [:a {:href (str "/user/" (:name row))} (:name row)]]
   [:td (:points row)]
   [:td (:turns row)]
   [:td (:deathdate row)]
   [:td [:a {:href (str "/cause/" (url-encode (:death_uniq row)))} (:death row)]]])

(defn rs-to-table [rs]
  [:table
   [:tr
    [:th "game"]
    [:th "name"]
    [:th "points"]
    [:th "turns"]
    [:th "deathdate"]
    [:th "death"]]
   (map #'rs-row-to-tr rs)])

(defn game [id]
  (sql/with-connection db
    (sql/with-query-results
      rs
      ["select * from xlogfile where id = ?" id]
      (let [row (first rs)]
        (if (not row)
          (page-not-found)
          (layout (make-dump-link row)))))))

(defn highscores
  ([] (highscores 10))
  ([limit]
     (sql/with-connection db
       (sql/with-query-results
         rs
         ["select * from xlogfile order by points desc limit ?" limit]
         (layout
          (rs-to-table rs))))))

(defn cause [cause-str]
  (sql/with-connection db
    (sql/with-query-results
      rs
      ["select * from xlogfile where death_uniq = ? order by points desc" cause-str]
      (layout
       (rs-to-table rs)))))

(defn last-games
  ([] (last-games 10))
  ([limit]
     (sql/with-connection db
       (sql/with-query-results
         rs
         ["select * from xlogfile order by endtime desc limit ?" limit]
         (layout
          (rs-to-table rs))))))

(defn user [name]
  (sql/with-connection db
    (sql/with-query-results
      rs
      ["select count(*) as games_played, (select count(*) from xlogfile where name = ? and death = 'ascended') as ascensions from xlogfile where name = ?" name name]
      (let [row (first rs)]
        (if (not row)
          (page-not-found)
          (layout
           [:div
            [:h2 name]
            [:p (:games_played row) " games played, " (:ascensions row) " ascensions."]
            [:a {:href (str "/user/" name "/dumps/")} "dumps"]
            [:br]
            [:a {:href (str "/user/" name "/ttyrecs/")} "ttyrecs"]
            [:br]
            [:br]
            (sql/with-query-results
              rs2
              ["select * from xlogfile where name = ? order by endtime desc limit 50" name]
              (doall rs2)
              (rs-to-table rs2))]))))))

(defn users []
  (sql/with-connection db
    (sql/with-query-results
      rs
      ["select distinct name from xlogfile order by name"]
      (layout
       [:div
        (map (fn [row]
               [:div [:a {:href (str "/user/" (:name row))} (:name row)]
                [:br]])
             rs)]))))

(defn causes []
  (sql/with-connection db
    (sql/with-query-results
      rs
      ["select distinct death_uniq as death, count(*) as count from xlogfile group by death order by count desc"]
      (layout
       (map (fn [row]
              [:div 
               (:count row) " " [:a {:href (str "/cause/" (url-encode (:death row)))} (:death row)]
               [:br]])
             rs)))))

(defroutes main-routes
  (GET "/" [] (last-games 25))
  (GET "/highscores" [] (highscores 25))
  (GET "/game/:id" [id] (game id))
  (GET "/user/:name" [name] (user name))
  (GET "/user/:name/" [name] (user name))
  (GET "/users" [] (users))
  (GET "/causes" [] (causes))
  (GET "/cause/:cause-str" [cause-str] (cause cause-str))
  (GET "/highscores/:limit" [limit] (highscores limit))
  (route/not-found (page-not-found)))

(defn start-web []
  (run-jetty #'main-routes {:port 8080 :join false}))

(defn stop-web [j]
  (.stop j))
