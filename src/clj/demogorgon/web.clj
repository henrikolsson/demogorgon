(ns demogorgon.web
  (:import [java.io File])
  (:use [compojure.core :only (defroutes GET)]
        [ring.adapter.jetty :only (run-jetty)]
        [ring.util.codec :only (url-encode url-decode)]
        [hiccup.core :only (html)]
        [hiccup.page :only (html5 include-js include-css)]
        [hiccup.util :only (escape-html)]
        [hiccup.element :only (link-to)]
        [demogorgon.nh :only (parse-bitfield conducts)]
        [demogorgon.config])
  (:require [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.util.response :as resp]
            [clojure.java.jdbc :as sql]
            [clojure.tools.logging :as log]))

(defn layout [& content]
  (html5
   [:head
    [:title "un.nethack.nu - unnethack public server"]
    (include-css "/css/bootstrap.min.css")
    (include-css "/css/darkstrap3.css")
    (include-css "/css/jquery.dataTables.css")
    (include-css "/css/jquery.dataTables_themeroller.css")
    (include-css "/css/demo_table_jui.css")
    (include-css "/css/ui-darkness/jquery-ui-1.10.3.custom.css")
    (include-css "/css/site.css")
    [:style {:type "text/css"}
     "body { padding-top: 50px; }"
     "#main { padding-top: 15px; }"]
    [:script {:type "text/javascript"}
     "var _gaq = _gaq || [];"
     "_gaq.push(['_setAccount', 'UA-22043040-1']);"
     "_gaq.push(['_trackPageview']);"
     "(function() {"
     "var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;"
     "ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';"
     "var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);"
     "})();"]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0"}]]
   [:body
    [:nav.navbar.navbar-default.navbar-fixed-top {:role "navigation"}
     [:div.container
      [:div.navbar-header
       [:button.navbar-toggle {"type" "button"
                               "data-toggle" "collapse"
                               "data-target" ".navbar-collapse"}
        [:span.sr-only "Toggle navigation"]
        [:span.icon-bar ""]
        [:span.icon-bar ""]
        [:span.icon-bar ""]]
       [:a.navbar-brand {"href" "/"} "un.nethack.nu"]]
      [:div.navbar-collapse.collapse
       [:ul.nav.navbar-nav
        [:li [:a#nav-dashboard {"href" "/last-games"} "last games"]]
        [:li [:a#nav-dashboard {"href" "/highscores"} "highscores"]]
        [:li [:a#nav-dashboard {"href" "/users"} "users"]]
        [:li [:a#nav-dashboard {"href" "/causes"} "causes"]]
        [:li [:a#nav-dashboard {"href" "/ascensions"} "ascensions"]]]]]]
    [:div#main.container
     content]
    (include-js "/js/demogorgon.js")
    (include-js "/js/jquery-1.9.1.js")
    (include-js "/js/jquery-ui-1.10.3.custom.js")
    (include-js "/js/jquery.dataTables.min.js")
    (include-js "/js/bootstrap.min.js")
    (include-js "/js/site.js")]))

(defn page-not-found []
  "<h1>Page not found</h1>")

(defn make-dump-link [row]
  (let [base (str "/user/" (:name row) "/dumps/" (:region row) "/" (:name row ) "."
                  (/ (.getTime (:endtime row)) 1000))
        file-base (str "/srv/un.nethack.nu" base)
        extension (if (.exists (File. (str file-base ".txt.html")))
                    ".txt.html"
                    (if (.exists (File. (str file-base ".txt")))
                      ".txt"
                      nil))]
    (if extension
      [:a {:href (str base extension)} (:id row)]
      (:id row))))

(defn pretty-date [date]
  (let [date (str date)]
    (if (= (count date) 8)
      (str (.substring date 0 4) "-"
           (.substring date 4 6) "-"
           (.substring date 6 8))
      date)))

(defn rs-row-to-tr [row]
  [:tr
   [:td (make-dump-link row)]
   [:td [:a {:href (str "/user/" (:name row))} (:name row)]]
   [:td (:points row)]
   [:td (:turns row)]
   [:td (pretty-date (:deathdate row))]
   [:td [:a {:href (str "/cause/" (url-encode (:death_uniq row)))} (:death row)]]])

(defn rs-row-to-tr-ascension [row]
  [:tr
   [:td (make-dump-link row)]
   [:td [:a {:href (str "/user/" (:name row))} (:name row)]]
   [:td (:points row)]
   [:td (:turns row)]
   [:td (pretty-date (:deathdate row))]
   [:td (str (count (:conducts row)) ": "
             (apply str (interpose ", " (:conducts row))))]])

(defn rs-to-table [rs]
  [:table
   [:thead
    [:tr
     [:th "game"]
     [:th "name"]
     [:th "points"]
     [:th "turns"]
     [:th "deathdate"]
     [:th "death"]]]
   [:tbody
    (map #'rs-row-to-tr rs)]])

(defn rs-to-table-ascension [rs]
  [:table
   [:thead
    [:tr
     [:th "game"]
     [:th "name"]
     [:th "points"]
     [:th "turns"]
     [:th "deathdate"]
     [:th "conducts"]]]
   [:tbody
    (map #'rs-row-to-tr-ascension rs)]])

(defn ascensions []
  (let [rows
        (map #(assoc %1 :conducts (parse-bitfield conducts (:conduct %1)))
             (let [rs (sql/query (:db @config) ["select * from xlogfile where death LIKE 'ascended%' order by points desc"])]
               (doall rs)))
        points-table (rs-to-table-ascension (take 10 rows))
        conducts-table (rs-to-table-ascension
                        (take 10
                              (sort-by #(* (count (:conducts %1)) -1) rows)))]
    (layout
     [:h2 "by points"]
     points-table
     [:h2 "by conducts"]
     conducts-table)))

(defn game [id]
  (let [rs (sql/query (:db @config) ["select * from xlogfile where id = ?" id])]
    (let [row (first rs)]
      (if (not row)
        (page-not-found)
        (layout (make-dump-link row))))))

(defn highscores
  ([] (highscores 10))
  ([limit]
   (let [rs (sql/query (:db @config) ["select * from xlogfile order by points desc limit ?" limit])]
     (layout
      (rs-to-table rs)))))

(defn cause [cause-str]
  (let [rs (sql/query (:db @config) ["select * from xlogfile where death_uniq = ? order by points desc limit 100" (url-decode cause-str)])]
    (layout
     (rs-to-table rs))))

(defn last-games []
  (let [rs (sql/query (:db @config) ["select * from xlogfile order by endtime desc limit ?" 25])]
    (resp/response (take 2 (map #(assoc %1 :key (get %1 :id)) rs)))))


(defn frontpage []
  (let [rs (sql/query (:db @config) ["select * from xlogfile order by endtime desc limit ?" 5])]
    (layout
     [:div 
      [:h2 "about"]
      [:p
       "un.nethack.nu is a public server for " [:a {:href "http://sourceforge.net/apps/trac/unnethack/"} "UnNetHack"]
       ". There's a " [:a {:href "telnet://eu.un.nethack.nu"} "european server (telnet)"] " and an "
       [:span {:style "text-decoration: line-through;"} "american server"] "."]
      
      [:h2 "links"]
      [:ul
       [:li [:a {:href "/default-unnethackrc"} "default rc-file"]]
       [:li [:a {:href "/logs"} "logfiles"]]]
      [:h2 "last 5 deaths"]
      (rs-to-table rs)])))

(defn user [name]
  (let [rs (sql/query (:db @config) ["select count(*) as games_played, (select count(*) from xlogfile where name = ? and death LIKE 'ascended%') as ascensions from xlogfile where name = ?" name name])]
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
          (let [rs2 (sql/query (:db @config) ["select * from xlogfile where name = ? order by endtime desc limit 100" name])]
            (doall rs2)
            (rs-to-table rs2))])))))

(defn users []
  (let [rs (sql/query (:db @config) ["select distinct name from xlogfile order by name"])]
    (layout
     [:div
      (map (fn [row]
             [:div [:a {:href (str "/user/" (:name row))} (:name row)]
              [:br]])
           rs)])))

(defn causes []
  (let [rs (sql/query (:db @config) ["select distinct death_uniq as death, count(*) as count from xlogfile group by death_uniq order by count desc"])]
    (layout
     (map (fn [row]
            [:div 
             (:count row) " " [:a {:href (str "/cause/" (url-encode (:death row)))} (:death row)]
             [:br]])
          rs))))

(defn index [req]
  (resp/content-type (resp/resource-response "index.html" {:root "public"}) "text/html;charset=utf-8"))

(defroutes main-routes
  (GET "/" [] index)
  (GET "/api/last-games" [] (last-games))
  (GET "/api/" [] (frontpage))
  (GET "/api/highscores" [] (highscores 25))
  (GET "/api/game/:id" [id] (game id))
  (GET "/api/user/:name" [name] (user name))
  (GET "/api/user/:name/" [name] (user name))
  (GET "/api/users" [] (users))
  (GET "/api/causes" [] (causes))
  (GET "/api/ascensions" [] (ascensions))
  (GET ["/api/cause/:cause-str", :cause-str #".+"] [cause-str] (cause cause-str))
  (GET "/api/highscores/:limit" [limit] (highscores limit))
  (route/resources "/")
  (route/not-found index))

(def app
  (-> main-routes
      (middleware/wrap-json-body {:keywords? true :bigdecimals? true})
      (middleware/wrap-json-response)
      (wrap-defaults api-defaults)))

(defn start-web []
  (run-jetty #'app {:port 8080 :join? false}))

(defn stop-web [j]
  (.stop j))
