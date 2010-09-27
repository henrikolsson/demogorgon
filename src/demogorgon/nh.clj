(ns demogorgon.nh
  (:import [java.io File FilenameFilter])
  (:require [clj-stacktrace.repl :as stacktrace])
  (:use	[clojure.contrib.duck-streams :only (reader)]))

(defn parse-line [line]
  (let [props (.split line ":")
        keys (map (fn [x] (keyword (aget (.split x "=") 0))) props)
        values (map (fn [x] (aget (.split x "=") 1)) props)]
    (zipmap keys values)))

(defn get-online-players []
  (let [dir (File. "/opt/nethack.nu/dgldir/inprogress/")
        filter (proxy [FilenameFilter] []
                   (accept [dir name]
                           (and (.endsWith name ".ttyrec")
                                (> (.indexOf name ":") 0))))
        files (seq (.list dir filter))]
    (map (fn [file]
             (let [tokens (.split file ":" 2)
                   player (aget tokens 0)
                   date (aget tokens 1)
                   ttyrec (File. (str "/opt/nethack.nu/dgldir/ttyrec/" player "/" date))
                   idle (unchecked-divide
                         (int (- (System/currentTimeMillis) (.lastModified ttyrec)))
                         1000)
                   whereis (File. (str "/opt/nethack.nu/var/unnethack/" player ".whereis"))]
               (with-open [rdr (reader whereis)]
                 (let [data (parse-line (first (line-seq rdr)))]
                   (assoc data :idle idle)))))
           files)))
      
(defn format-time [secs]
  (if (< secs 60)
    (str secs "s")
    (let [minutes (.intValue (Math/floor (/ secs 60)))
          secs (mod secs 60)]
      (if (< minutes 60)
        (str minutes "m " secs "s")
        (let [hours (.intValue (Math/floor (/ minutes 60)))
              minutes (mod minutes 60)]
          (str hours "h " minutes "m " secs "s"))))))

(defn online-players-hook [irc object match]
  (let [players (get-online-players)]
    (if (empty? players)
      "the world of unnethack is currently empty.. why don't you give it a try?"
      (map (fn [data]
             (format "%s the %s %s %s %s is currently at level %s in %s at turn %s%s (idle for %s)"
                     (:player data)
                     (:align data)
                     (:gender data)
                     (:race data)
                     (:role data)
                     (:depth data)
                     (:dname data)
                     (:turns data)
                     "" ;; FIXME - Amulet
                     (format-time (:idle data))))
           players))))
