(ns demogorgon.nh
  (:import [java.io File FilenameFilter RandomAccessFile ByteArrayOutputStream]
           [org.apache.log4j Logger]
           [java.nio.file FileSystems StandardWatchEventKinds])
  (:require [clj-stacktrace.repl :as stacktrace]
            [tachyon.core :as irc]
            [demogorgon.twitter :as twitter]
            [clojure.contrib.sql :as sql])
  (:use	[clojure.contrib.duck-streams :only (reader read-lines)]
        [demogorgon.config]))

(defstruct watcher-instance :watch-service :events :thread :irc)

(def logger (Logger/getLogger "demogorgon.nh"))
(def scummers (ref {}))
(def announce-region "eu")

(def db {:classname "org.sqlite.JDBC"
                    :subprotocol "sqlite"
                    :subname "/tmp/nh.db"
                    :create true})
(def roles
     {"arc" "Archeologist"
      "bar" "Barbarian"
      "cav" "Caveman"
      "hea" "Healer"
      "kni" "Knight"
      "mon" "Monk"
      "pri" "Priest"
      "rog" "Rogue"
      "ran" "Ranger"
      "sam" "Samurai"
      "tou" "Tourist"
      "val" "Valkyrie"
      "wiz" "Wizard"
      "vam" "Vampire"})
(def races
     {"hum" "human"
      "orc" "orcish"
      "gno" "gnomish"
      "elf" "elven"
      "dwa" "dwarven"})
(def genders
     {"fem" "female"
      "mal" "male"
      "ntr" "neuter"})
(def alignments
     {"law" "lawful"
      "cha" "chaotic"
      "neu" "neutral"
      "una" "evil"})

(def zonemap
     ["in The Dungeons of Doom"
      "in Gehennom"
      "in The Gnomish Mines"
      "in The Quest"
      "in Sokoban"
      "in Town"
      "in Fort Ludios"
      "in the Black Market"
      "in Vlad's Tower"
      "on The Elemental Planes"
      "in the Advent Calendar"])

(def achievements
     ["obtained the Bell of Opening"
      "entered Gehennom"
      "obtained the Candelabrum of Invocation"
      "obtained the Book of the Dead"
      "performed the invocation ritual"
      "obtained the amulet"
      "entered the elemental planes"
      "entered the astral plane"
      "ascended"
      "obtained the luckstone from the Mines"
      "obtained the sokoban prize"
      "defeated Medusa"])

(def conducts
     ["foodless"
      "vegan"
      "vegetarian"
      "atheist"
      "weaponless"
      "pacifist"
      "illiterate"
      "polyless"
      "polyselfless"
      "wishless"
      "artifact wishless"
      "genocideless"])

(defn region-from-fn [fn]
  (if (.endsWith fn "-us")
    "us"
    "eu"))

(defn zone [zonenum]
  (let [i (if (isa? (class zonenum) String)
            (Integer/parseInt zonenum)
            zonenum)]
    (if (>= i (count zonemap))
      "in an unknown zone"
      (nth zonemap i))))
      
(defn role [role]
  (get roles (.toLowerCase role) role))

(defn race [race]
  (get races (.toLowerCase race) race))

(defn gender [gender]
  (get genders (.toLowerCase gender) gender))

(defn possessive-gender [gender]
  (let [lg (.toLowerCase gender)]
    (if (= lg "mal")
      "His"
      (if (= lg "fem")
        "Her"
        "It's"))))

(defn possessive [name]
  (if (.endsWith name "s")
    (str name "'")
    (str name "'s")))

(defn alignment [alignment]
  (get alignments (.toLowerCase alignment) alignment))

(defn parse-line [line]
  (let [props (.split line ":")
        keys (map (fn [x] (keyword (aget (.split x "=") 0))) props)
        values (map (fn [x] (let [tokens (.split x "=")]
                              (if (> (alength tokens) 1)
                                (aget tokens 1)
                                ""))) props)]
    (zipmap keys values)))

(defn insert-xlogfile-line-db [region line]
  (let [data (assoc (parse-line line) :region region)]
    (let [record (assoc data :death_uniq (.replaceAll (:death data) ", while .*$", ""))]
      (sql/insert-records :xlogfile
                        record))))

(defn sanitize-nick [nick]
  (.replaceAll nick "[^\\p{Alnum}]" ""))

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
                   idle (int (/
                              (int (- (System/currentTimeMillis) (.lastModified ttyrec)))
                              1000))
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

(defn get-last-dump-url [nick]
  (let [exts [".txt.html" ".txt"]
        file (first
              (filter #(.exists %)
                      (map #(File. (str "/srv/un.nethack.nu/users/" nick "/dumps/" nick ".last" %)) exts)))]
    (if file
      (let [fp (.getCanonicalPath file)]
        (str "http://un.nethack.nu/user/" nick "/dumps" (.substring fp (.lastIndexOf fp "/"))))
      nil)))
        
(defn last-dump-hook [irc object match]
  (let [nick (sanitize-nick (if (= (second match) "")
                              (:nick (:prefix object))
                              (second match)))
        url (get-last-dump-url nick)]
    (if url
      url
      (str "No dumpfile for " nick))))

(defn add-scum [player]
  (.info logger (str "adding scum " player))
  (dosync
   (alter scummers assoc player (+ (System/currentTimeMillis) 30000))))

(defn is-scum [player]
  (dosync 
   (if (contains? @scummers player)
     (if (< (get @scummers player) (System/currentTimeMillis))
       (do
         (.info logger "removing scum " player)
         (alter scummers dissoc player)
         false)
       true))))

(defn online-players-hook [irc object match]
  (let [players (get-online-players)]
    (if (empty? players)
      "the world of unnethack is currently empty.. why don't you give it a try?"
      (map (fn [data]
             (format "%s the %s %s %s %s is currently at level %s in %s at turn %s%s (idle for %s)"
                     (:player data)
                     (alignment (:align data))
                     (gender (:gender data))
                     (race (:race data))
                     (role (:role data))
                     (:depth data)
                     (:dname data)
                     (:turns data)
                     (if (= (:amulet data) "1")
                       " (carrying the amulet)"
                       "")
                     (format-time (:idle data))))
           players))))

(defn announce [irc msg]
  (doseq [channel (:channels (:irc config))]
    (irc/send-message irc channel msg)))

(defn truncate [str max]
  (if (> (.length str) max)
    (.substring str 0 max)
    str))

(defn handle-xlogfile-line [irc file line]
  (sql/with-connection db
    (sql/transaction
     (insert-xlogfile-line-db (region-from-fn file) line)))
  (let [data (assoc (parse-line line) :region (region-from-fn file))]
    (if (and (or (< (Integer/parseInt (:turns data)) 10)
                 (< (Integer/parseInt (:points data)) 10))
             (or (= (:death data) "quit")
                 (= (:death data) "escaped")))
      (add-scum (:player data)))
    (if (not (is-scum (:player data)))
      (let [out (format "%s, the %s %s %s %s%s %s score was %s."
                        (:name data)
                        (alignment (:align data))
                        (gender (:gender data))
                        (race (:race data))
                        (role (:role data))
                        (if (.startsWith (:death data) "ascended")
                          (str " " (:death data) " to demigod-hood.")
                          (format ", left this world %s on level %s, %s."
                                  (zone (:deathdnum data))
                                  (:deathlev data)
                                  (:death data)))
                        (possessive-gender (:gender data))
                        (:points data))
            tweet-prefix (truncate (format "%s (%s %s %s %s), %s points, %s turns, %s"
                                           (:name data)
                                           (:role data)
                                           (:race data)
                                           (:gender data)
                                           (:align data)
                                           (:points data)
                                           (:turns data)
                                           (:death data))
                                   120)
            url (twitter/shorten-url (format "http://un.nethack.nu/user/%s/dumps/%s.%s.txt.html"
                                             (:name data)
                                             (:name data)
                                             (:endtime data)))
            final-tweet (str tweet-prefix ", " url)]
        (if (= (:region data) announce-region)
          (announce irc out))
        (try 
         (twitter/tweet final-tweet)
         (catch Exception e
           (.error logger "tweet failed" e)))))))

(condp = "euas"
  "us" "american"
  "eu" "european"
  "us")

(defn friendly-region [rgn]
  "terrifying")
  (condp = rgn
    "us" "american"
    "eu" "european"
    rgn))
    
(defn make-game-action-out [data]
  (condp = (:game_action data)
    "started" (if (:character data)
                (format "%s enters the %s dungeon as a%s."
                        (:player data)
                        (friendly-region (:region data))
                        (:character data))
                (format "%s enters the %s dungeon as a%s %s %s."
                        (:player data)
                        (friendly-region (:region data))
                        (:alignment data)
                        (race (:race data))
                        (role (:role data))))
    "resumed" (if (:character data)
                (format "%s the%s resumes the adventure in the %s realm."
                        (:player data)
                        (:character data)
                        (friendly-region (:region data)))
                (format "%s the %s %s resumes the adventure in the %s realm."
                        (:player data)
                        (race (:race data))
                        (role (:role data))
                        (friendly-region (:region data))))
    
    "saved" (format "%s is taking a break from the hard life as an adventurer."
                    (:player data))

    "panicked" (format "The dungeon of %s collapsed after %s turns!"
                       (:player data)
                       (:turns data))))

(defn bitfield-to-int [bitfield]
  (if (string? bitfield)
    (if (or (.startsWith bitfield "0x")
            (.startsWith bitfield "0X"))
      (Integer/parseInt (.substring bitfield 2) 16)
      (Integer/parseInt bitfield))
    bitfield))

(defn parse-bitfield [values bitfield]
  (let [bitfield (if (integer? bitfield)
                   bitfield
                   (bitfield-to-int bitfield))]
    (filter string?
            (map (fn [idx]
                   (if (bit-test bitfield idx)
                     (nth values idx)
                     nil))
                 (range (count values))))))

(defn make-livelog-out [data]
  (condp #(contains? %2 %1) data
    :wish (if (= (.toLowerCase (:wish data)) "nothing")
            (str (:player data) " has declined a wish")
            (str (:player data) " wished for '" (:wish data) "' after " (:turns data) " turns."))
    
    :shout (format "You hear %s distant rumbling%s"
                   (possessive (:player data))
                   (if (= (:shout data) "")
                     "."
                     (format ": \"%s\"" (:shout data))))
    
    :genocided_monster (format "%s genocided %s%s"
                               (:player data)
                               (if (= (:dungeon_wide data) "yes")
                                 "all "
                                 "")
                               (:genocided_monster data))
    
    :killed_uniq (if (not (= (:killed_uniq data) "Medusa"))
                   (str (:player data) " killed " (:killed_uniq data) " after " (:turns data) " turns."))
    
    :killed_shopkeeper (str (:player data) " killed the shopkeeper "
                            (:killed_shopkeeper data) " after " (:turns data) " turns")
    
    :shoplifted (format "%s stole %s zorkmids worth of merchandise from %s %s after %s turns"
                        (:player data)
                        (:shoplifted data)
                        (possessive (:shopkeeper data))
                        (:shop data)
                        (:turns data))
    
    :bones_killed (format "%s killed the %s of %s the former %s after %s turns."
                          (:player data)
                          (:bones_monst data)
                          (:bones_killed data)
                          (:bones_rank data)
                          (:turns data))
    
    :crash (format "%s has defied the laws of unnethack, process exited with status %s"
                   (:player data)
                   (:crash data))
    
    :sokobanprize (format "%s obtained %s after %s turns in Sokoban."
                          (:player data)
                          (:sokobanprize data)
                          (:turns data))
    
    :game_action (make-game-action-out data)

    :achieve_diff (let [achieve-diff (bitfield-to-int (:achieve_diff data))]
                    (if (not (or (= achieve-diff 0)
                                 (= achieve-diff 0x200)
                                 (= achieve-diff 0x400)))
                      (format "%s %s after %s turns."
                              (:player data)
                              (first (parse-bitfield achievements achieve-diff))
                              (:turns data))))

    (str "unhandled line: " data)))

(defn handle-livelog-line [irc file line]
  (let [data (assoc (parse-line line) :region (region-from-fn file))]
    (if (and (not (is-scum (:player data)))
             (= (:region data) announce-region))
      (let [out (make-livelog-out data)]
        (if out
          (announce irc out))))))

; read a line from a RandomAccessFile, in utf-8
(defn read-line-ra [ra]
  (let [buffer (ByteArrayOutputStream.)]
    (loop [b (.read ra)]
      (if (= b -1)
        (if (= (.size buffer) 0)
          nil
          (String. (.toByteArray buffer) "UTF-8"))
        (do
          (if (= b 0x0a)
            (String. (.toByteArray buffer) "UTF-8")
            (do 
              (.write buffer b)
              (recur (.read ra)))))))))
        

(defn run-watcher [watcher]
  (with-local-vars [files {"livelog"   {:length (.length (File. (str (:un-dir config) "livelog")))
                                        :callback #'handle-livelog-line}
                           "livelog-us"   {:length (.length (File. (str (:un-dir config) "livelog-us")))
                                        :callback #'handle-livelog-line}                           
                           "xlogfile"  {:length (.length (File. (str (:un-dir config) "xlogfile")))
                                        :callback #'handle-xlogfile-line}
                           "xlogfile-us"  {:length (.length (File. (str (:un-dir config) "xlogfile-us")))
                                        :callback #'handle-xlogfile-line}}]
    (while
     (not (Thread/interrupted))
     (.debug logger (str "Waiting for events.."))
     (let [key (.take (:watch-service @watcher))]
       (.debug logger "Got events")
       (doseq [event (.pollEvents key)]
         (let [fn (.toString (.context event))
               file (get (var-get files) fn)]
           (if file
             (do
               (.debug logger (str "file modified: " fn))
               (let [ra (RandomAccessFile. (str (:un-dir config) fn) "r")]
                 (.seek ra (:length file))
                 (loop [line (read-line-ra ra)]
                   (if line
                     (do 
                       ((:callback file) (:irc @watcher) fn line)
                       (var-set files (assoc (var-get files)
                                        fn
                                        (assoc (get (var-get files) fn) :length (.getFilePointer ra))))
                       (recur (read-line-ra ra)))))
                 (.close ra)))))
         (.reset key))))))

(defn nh-init [irc]
  (let [watcher (ref (struct-map watcher-instance
                       :watch-service (.newWatchService (FileSystems/getDefault))
                       :thread nil
                       :irc irc))]
    (dosync
     (ref-set watcher (assoc @watcher
                        :thread (Thread. (proxy [Runnable] []
                                           (run []
                                                (run-watcher watcher)))))))
    (.register (.toPath (File. (:un-dir config)))
               (:watch-service @watcher)
               (into-array [StandardWatchEventKinds/ENTRY_MODIFY]))
    (.setUncaughtExceptionHandler
     (:thread @watcher)
     (proxy [Thread$UncaughtExceptionHandler] []
       (uncaughtException [thread throwable]
                          (.error logger "bah" throwable)
                          (stacktrace/pst throwable))))
    watcher))

(defn nh-stop [watcher]
  (.close (:watch-service @watcher)))

(defn nh-init-db []
  (.info logger "re-initializing database..")
  (if (.exists (File. "/tmp/nh.db"))
    (.delete (File. "/tmp/nh.db")))
  (sql/with-connection db
    (sql/transaction
     (sql/create-table
      :xlogfile
      [:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
      [:version "TEXT"]
      [:points "INT"]
      [:deathdnum "INT"]
      [:deathdname "TEXT"]
      [:deathlev "INT"]
      [:maxlvl "INT"]
      [:dlev_name "TEXT"]
      [:hp "INT"]
      [:maxhp "INT"]
      [:deaths "INT"]
      [:deathdate "DATE"]
      [:birthdate "DATE"]
      [:uid "INT"]
      [:role "TEXT"]
      [:race "TEXT"]
      [:gender "TEXT"]
      [:align "TEXT"]
      [:name "TEXT"]
      [:death "TEXT"]
      [:death_uniq "TEXT"]
      [:conduct "TEXT"]
      [:turns "INT"]
      [:achieve "TEXT"]
      [:realtime "INT"]
      [:starttime "TIMESTAMP"]
      [:endtime "TIMESTAMP"]
      [:gender0 "TEXT"]
      [:align0 "TEXT"]
      [:flags "TEXT"]
      [:region "TEXT"])
     (let [lines (read-lines (str (:un-dir config) "xlogfile"))]
       (doseq [line lines]
         (insert-xlogfile-line-db "eu" line)))
     (let [lines (read-lines (str (:un-dir config) "xlogfile-us"))]
       (doseq [line lines]
         (insert-xlogfile-line-db "us" line)))))
  (.info logger "database initialized"))

(defn nh-start [watcher]
  (nh-init-db)
  (.start (:thread @watcher)))

(defn nh-run [watcher]
  (.start (:thread @watcher)))
