(ns demogorgon.nh
  (:import [java.io File FilenameFilter RandomAccessFile]
           [org.apache.log4j Logger])
  (:require [clj-stacktrace.repl :as stacktrace]
            [tachyon.core :as irc]
            [demogorgon.twitter :as twitter])
  (:use	[clojure.contrib.duck-streams :only (reader)]
        [demogorgon.config]))

(def logger (Logger/getLogger "demogorgon.nh"))
(def scummers (ref {}))

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
      "on The Elemental Planes"])

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

(defn get-last-dump-url [nick]
  (let [exts [".txt.html" ".txt"]
        file (first
              (filter #(.exists %)
                      (map #(File. (str "/srv/un.nethack.nu/users/" nick "/dumps/" nick ".last" %)) exts)))]
    (if file
      (let [fp (.getCanonicalPath file)]
        (str "http://un.nethack.nu/users/" nick "/dumps" (.substring fp (.lastIndexOf fp "/"))))
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

(defn file-line-poller-loop [fn callback]
  (let [fd (File. fn)]
    (loop [length (Long/valueOf (.length fd))]
      (Thread/sleep 1000)
      (with-local-vars [new-length length]
        (if (> (.length fd) length)
          (let [ra (RandomAccessFile. fd "r")]
            (.seek ra length)
            (let [line (.readLine ra)]
              (if line
                (do
                  (callback line)
                  ;; assumes ascii..
                  (var-set new-length (.getFilePointer ra)))))))
        (if (not (Thread/interrupted))
          (recur (var-get new-length)))))))

(defn announce [irc msg]
  (doseq [channel (:channels (:irc config))]
    (irc/send-message irc channel msg)))

(defn create-file-line-poller [fn callback]
  (let [thread (Thread. (proxy [Runnable] []
                          (run []
                               (file-line-poller-loop fn callback)))
                        (str "poller-" fn))]
    (.setDaemon thread true)
    thread))

(defn truncate [str max]
  (if (> (.length str) max)
    (.substring str 0 max)
    str))

(defn handle-xlogfile-line [irc line]
  (let [data (parse-line line)]
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
                        (if (= (:death data) "ascended")
                          " ascended to demigod-good."
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
            url (twitter/shorten-url (format "http://un.nethack.nu/users/%s/dumps/%s.%s.txt.html"
                                             (:name data)
                                             (:name data)
                                             (:endtime data)))
            final-tweet (str tweet-prefix ", " url)]
        (announce irc out)
        (twitter/tweet final-tweet)))))

(defn make-game-action-out [data]
  (condp = (:game_action data)
    "started" (if (:character data)
                (format "%s enters the dungeon as a%s."
                        (:player data)
                        (:character data))
                (format "%s enters the dungeon as a%s %s %s."
                        (:player data)
                        (:alignment data)
                        (race (:race data))
                        (role (:role data))))
    "resumed" (if (:character data)
                (format "%s the%s resumes the adventure."
                        (:player data)
                        (:character data))
                (format "%sthe %s %s resumes the adventure."
                        (:player data)
                        (race (:race data))
                        (role (:role data))))
    
    "saved" (format "%s is taking a break from the hard life as an adventurer."
                    (:player data))

    "panicked" (format "The dungeon of %s collapsed after %s turns!"
                       (:player data)
                       (:turns data))))

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
    
    :killed_uniq (str (:player data) " killed " (:killed_uniq data) " after " (:turns data))
    
    :shoplifted (format "%s stole %s zorkmids worth of merchandise from %s%s %s after %s turns"
                        (:player data)
                        (:shoplifted data)
                        (:shopkeeper data)
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

    :achieve (let [achieve-diff (Integer/parseInt (.substring "0x800" 2) 16)]
               (if (not (or (= achieve-diff 0)
                            (= achieve-diff 0x200)
                            (= achieve-diff 0x400)))
                 (first (filter #(not (= nil %1))
                                (map (fn [idx]
                                       (if (bit-test achieve-diff idx)
                                         (format "%s %s after %s turns."
                                                 (:player data)
                                                 (nth achievements idx)
                                                 (:turns data))
                                         nil))
                                     (range (count achievements)))))))
    (str "unhandled line: " data)))

(defn handle-livelog-line [irc line]
  (let [data (parse-line line)]
    (if (not (is-scum (:player data)))
      (announce irc (make-livelog-out data)))))

(defn nh-init [irc]
  (let [threads [(create-file-line-poller (:xlogfile config) (partial #'handle-xlogfile-line irc))
                 (create-file-line-poller (:livelog config) (partial #'handle-livelog-line irc))]]
    threads))

(defn nh-start [nh]
  (doseq [thread nh]
   (.setUncaughtExceptionHandler thread (proxy [Thread$UncaughtExceptionHandler] []
    (uncaughtException [thread throwable]
    (stacktrace/pst throwable))))
    (.start thread)))

(defn nh-stop [nh]
  (doseq [thread nh]
    (.interrupt thread)))

