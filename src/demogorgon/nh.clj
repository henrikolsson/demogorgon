(ns demogorgon.nh
  (:import [java.io File FilenameFilter RandomAccessFile ByteArrayOutputStream]
           [org.slf4j Logger LoggerFactory]
           [java.nio.file FileSystems StandardWatchEventKinds]
           [java.sql Date Timestamp])
  (:require [clj-stacktrace.repl :as stacktrace]
            [tachyon.core :as irc]
            [demogorgon.twitter :as twitter]
            [clojure.java.jdbc :as sql])
  (:require [clojure.core.async :as async :refer [close! go chan put! <!]])
  (:use	[clojure.java.io :only (reader)]
        [demogorgon.util :only (read-lines)]
        [demogorgon.watch :only (start-watcher stop-watcher create-watcher)]
        [demogorgon.config]))

(defstruct watcher-instance :watch-service :events :thread :irc)

(def ^{:private true} logger (LoggerFactory/getLogger "demogorgon.nh"))
(def scummers (ref {}))
(def announce-region "eu")

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

; Hash of special named dungeon branches
(def zonemap
     ; only map those dungeon branches that need special treatment
     {"One-eyed Sam's Market" "in the Black Market"
      "The Elemental Planes" "on the Elemental Planes"
      "Advent Calendar" "in the Advent Calendar"})

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

; return zonename with correct location preposition
(defn zone [zonename]
  (get zonemap zonename
    (str "in " zonename)))

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

(defn fdate [s]
  (str (.substring s 0 4)
       "-"
       (.substring s 4 6)
       "-"
       (.substring s 6 8)))

(defn map-types [m]
  (assoc m
    :points (if (:points m) (Integer/parseInt (:points m)) (:points m))
    :deathdnum (if (:deathdnum m) (Integer/parseInt (:deathdnum m)) (:deathdnum m))
    :deathlev (if (:deathlev m) (Integer/parseInt (:deathlev m)) (:deathlev m))
    :maxlvl (if (:maxlvl m) (Integer/parseInt (:maxlvl m)) (:maxlvl m))
    :hp (if (:hp m) (Integer/parseInt (:hp m)) (:hp m))
    :maxhp (if (:maxhp m) (Integer/parseInt (:maxhp m)) (:maxhp m))
    :deaths (if (:deaths m) (Integer/parseInt (:deaths m)) (:deaths m))
    :uid (if (:uid m) (Integer/parseInt (:uid m)) (:uid m))
    :turns (if (:turns m) (Integer/parseInt (:turns m)) (:turns m))
    :realtime (if (:realtime m) (Integer/parseInt (:realtime m)) (:realtime m))
    :elbereths (if (:elbereths m) (Integer/parseInt (:elbereths m)) (:elbereths m))
    :xplevel (if (:xplevel m) (Integer/parseInt (:xplevel m)) (:xplevel m))
    :exp (if (:exp m) (Integer/parseInt (:exp m)) (:exp m))
    :gold (if (:gold m) (Integer/parseInt (:gold m)) (:gold m))
    :endtime (if (:endtime m) (Timestamp. (* 1000 (Integer/parseInt (:endtime m)))) (:endtime m))
    :starttime (if (:starttime m) (Timestamp. (* 1000 (Integer/parseInt (:starttime m)))) (:starttime m))
    :deathdate (if (:deathdate m) (Date/valueOf (fdate (:deathdate m))) (:deathdate m))
    :birthdate (if (:birthdate m) (Date/valueOf (fdate (:birthdate m))) (:birthdate m))))

         
(defn insert-xlogfile-line-db [region line pos]
  (let [data (assoc (parse-line line) :region region :fpos pos)]
    (let [record (assoc data :death_uniq (.replaceAll (:death data) ", while .*$", ""))]
      (sql/insert-records :xlogfile
                          (map-types record)))))

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
  nil)
  ;; (dosync
  ;;  (if (contains? @scummers player)
  ;;    (if (< (get @scummers player) (System/currentTimeMillis))
  ;;      (do
  ;;        (.info logger (str "removing scum " player))
  ;;        (alter scummers dissoc player)
  ;;        false)
  ;;      true))))

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

(defn format-turns [turns]
  (if (= (Integer/parseInt turns) 1)
    "1 turn"
    (str turns " turns")))

(defn whereis-hook [irc object match]
  (let [player (sanitize-nick (if (= (second match) "")
                              (:nick (:prefix object))
                              (second match)))
        filename (str "/opt/nethack.nu/var/unnethack/" player ".whereis")]
    (if (not (.exists (File. filename)))
      (str player " doesn't seem to have played here yet.")
      (let [whereis (File. filename)]
        (with-open [rdr (reader whereis)]
          (let [data (parse-line (first (line-seq rdr)))
                out (format "%s %s on level %s in %s after %s."
                 (:player data)
                 (cond (= (:ascended data) "1") "died"
                       (= (:ascended data) "2") "ascended"
                       (= (:playing data)  "1") "is"
                       (= (:playing data)  "0") "saved"
                       :else "did something")
                 (:depth data)
                 (:dname data)
                 (format-turns (:turns data))
                )]
            out))))))


(defn announce [irc msg]
  (doseq [channel (:channels (:irc @config))]
    (irc/send-message irc channel msg)))

(defn truncate [str max]
  (if (> (.length str) max)
    (.substring str 0 max)
    str))

        ;; (try 
        ;;  (twitter/tweet final-tweet)
        ;;  (catch Exception e
        ;;    (.error logger "tweet failed" e)))))))
        ;;     tweet-prefix (truncate (format "%s (%s %s %s %s), %s points, %s turns, %s"
        ;;                                    (:name data)
        ;;                                    (:role data)
        ;;                                    (:race data)
        ;;                                    (:gender data)
        ;;                                    (:align data)
        ;;                                    (:points data)
        ;;                                    (:turns data)
        ;;                                    (:death data))
        ;;                            120)
        ;;     url (twitter/shorten-url (format "http://un.nethack.nu/user/%s/dumps/%s.%s.txt.html"
        ;;                                      (:name data)
        ;;                                      (:name data)
        ;;                                      (:endtime data)))
        ;;     final-tweet (str tweet-prefix ", " url)]

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
                        (:character data)
                (format "%s the %s %s resumes the adventure."
                        (:player data)
                        (race (:race data))
                        (role (:role data)))))
    
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

; read a line from a RandomAccessFile, in utf-8
(defn read-line-ra [ra]
  (let [buffer (ByteArrayOutputStream.)
        pos (.getFilePointer ra)]
    (loop [b (.read ra)]
      (if (= b -1)
        (do
          (.seek ra pos)
          nil)
        (do
          (if (= b 0x0a)
            (String. (.toByteArray buffer) "UTF-8")
            (do 
              (.write buffer b)
              (recur (.read ra)))))))))
        
(defn nh-init [irc]
  {:watcher (create-watcher [(:un-dir @config)] {:recursive true})
   :irc irc})

(defn nh-stop [nh]
  (stop-watcher (:watcher nh)))

(defn update-xlogfile [region position announce? irc]
  (sql/with-connection (:db @config)
    (sql/transaction
     (let [ra (RandomAccessFile.
               (str (:un-dir @config)
                    (File/separator) region (File/separator) "xlogfile")
               "r")]
       (.info logger (str (:un-dir @config)
                          (File/separator) region (File/separator) "xlogfile"))
       (.info logger (str "ra: " ra))
       (.seek ra position)
       (loop [line (read-line-ra ra)]
         (if line
           (do
             (if announce?
               (let [data (assoc (parse-line line) :region region)]
                 (if (and (or (< (Integer/parseInt (:turns data)) 10)
                              (< (Integer/parseInt (:points data)) 10))
                          (or (= (:death data) "quit")
                              (= (:death data) "escaped")))
                   (add-scum (:name data)))
                 (if (not (is-scum (:name data)))
                   (let [out (format "[%s] %s, the %s %s %s %s%s %s score was %s."
                                     region
                                     (:name data)
                                     (alignment (:align data))
                                     (gender (:gender data))
                                     (race (:race data))
                                     (role (:role data))
                                     (if (.startsWith (:death data) "ascended")
                                       (str " " (:death data) " to demigod-hood.")
                                       (format ", left this world %s on level %s, %s."
                                               (zone (:deathdname data))
                                               (:deathlev data)
                                               (:death data)))
                                     (possessive-gender (:gender data))
                                     (:points data))]
                     (announce irc out)))))
             (insert-xlogfile-line-db region line (.getFilePointer ra))
             (recur (read-line-ra ra)))))
       (let [pos (.getFilePointer ra)]
         (.close ra)
         pos)))))

(defn handle-livelog [region position irc]
  (let [ra (RandomAccessFile.
            (str (:un-dir @config)
                 (File/separator) region (File/separator) "livelog")
            "r")]
    (.info logger (str (:un-dir @config)
                       (File/separator) region (File/separator) "livelog"))
    (.info logger (str "ra: " ra))
    (.seek ra position)
    (loop [line (read-line-ra ra)]
      (if line
        (let [data (assoc (parse-line line) :region region)
              out (make-livelog-out data)]
          (if out
            (announce irc (format "[%s] %s" region out)))
          (recur (read-line-ra ra)))))
    (let [pos (.getFilePointer ra)]
         (.close ra)
         pos)))

(defn get-xlogfile-positions []
  (let [regions {:eu 0
                 :us 0}]
    (into regions
          (sql/with-connection (:db @config)
            (sql/with-query-results
              rs
              ["select region, max(fpos) as fpos from xlogfile group by region"]
              (doall (map (fn [row]
                            [(keyword (:region row)) (:fpos row)])
                          rs)))))))

(defn nh-init-db []
  (let [regions (get-xlogfile-positions)]
    (.info logger "initializing database..")
    (doseq [region (keys regions)]
      (.info logger (str (name region) " pos " (get regions region)))
      (.info logger (str (name region) " to "
                         (update-xlogfile (name region) (get regions region) false nil))))
    (.info logger "database initialized")))

(defn file-length [f]
  (.length (File. f)))

(defn handle-file-update [nh path state]
  (try
    (.info logger (str "in: " state))
    (let [cnt (.getNameCount path)
          region (keyword (.toString (.getName path (- cnt 2))))
          file (.toString (.getName path (- cnt 1)))]
      (.info logger (str region " - " file " - " cnt))
      (.info logger (str (get state region)))
      (.info logger (str (get (get state region) :xlogfile)))
      (if (.endsWith file "xlogfile")
        (let [foo (update-xlogfile (name region) (get (get state region) :xlogfile) true (:irc nh))
              new-state (assoc-in state [region :xlogfile] foo)]
          (.info logger (str "foox: " foo))
          (.info logger (str "new state: " new-state))
          new-state)
        (if (.endsWith file "livelog")
          (let [new-state
                (assoc-in state [region :livelog] (handle-livelog (name region) (get (get state region) :livelog) (:irc nh)))]
            (.info logger (str "new state: " new-state))
            new-state)
          state)))
    (catch Exception e
      (.error logger "file update failed" e)
      (.info logger (str "ret: " state))
      state)))

(defn nh-run [nh]
  (let [chan (start-watcher (:watcher nh))]
    (go 
     (loop [state {:us {:xlogfile (file-length (str (:un-dir @config) "/us/xlogfile"))
                        :livelog (file-length (str (:un-dir @config) "/us/livelog"))}
                   :eu {:xlogfile (file-length (str (:un-dir @config) "/eu/xlogfile"))
                        :livelog (file-length (str (:un-dir @config) "/eu/livelog"))}}
            event (<! chan)]
       (.info logger (str "event: " event))
       (if event
           (let [new-state (handle-file-update nh (second event) state)]
             (.info logger (str "recur, state: " new-state))
             (recur new-state (<! chan))))))))
           

(defn nh-start [nh]
  (nh-init-db)
  (nh-run nh))


;; demogorgon.core.bot

;; (def asdf (nh-init nil))

;; (def chanx (nh-run asdf))


;; (nh-stop asdf)
;; (close! chanx)


;(swap! config merge {:un-dir "/home/henrik/tmp/un"})




;; (defn run-watcher [watcher]
;;   (with-local-vars [files {"livelog"   {:length (.length (File. (str (:un-dir @config) "livelog")))
;;                                         :callback #'handle-livelog-line}
;;                            "livelog-us"   {:length (.length (File. (str (:un-dir @config) "livelog-us")))
;;                                         :callback #'handle-livelog-line}                           
;;                            "xlogfile"  {:length (.length (File. (str (:un-dir @config) "xlogfile")))
;;                                         :callback #'handle-xlogfile-line}
;;                            "xlogfile-us"  {:length (.length (File. (str (:un-dir @config) "xlogfile-us")))
;;                                         :callback #'handle-xlogfile-line}}]
;;     (while
;;      (not (Thread/interrupted))
;;      (.debug logger (str "Waiting for events.."))
;;      (let [key (.take (:watch-service @watcher))]
;;        (.debug logger "Got events")
;;        (doseq [event (.pollEvents key)]
;;          (let [fn (.toString (.context event))
;;                file (get (var-get files) fn)]
;;            (if file
;;              (do
;;                (.debug logger (str "file modified: " fn))
;;                (let [ra (RandomAccessFile. (str (:un-dir @config) fn) "r")]
;;                  (.seek ra (:length file))
;;                  (loop [line (read-line-ra ra)]
;;                    (if line
;;                      (do 
;;                        ((:callback file) (:irc @watcher) fn line)
;;                        (var-set files (assoc (var-get files)
;;                                         fn
;;                                         (assoc (get (var-get files) fn) :length (.getFilePointer ra))))
;;                        (recur (read-line-ra ra)))))
;;                  (.close ra)))))
;         (.reset key))))))


;; (defn handle-xlogfile-line [irc file line]  
;;   (sql/with-connection (:db @config)
;;     (sql/transaction
;;      (insert-xlogfile-line-db (region-from-fn file) line)))
;;   (let [data (assoc (parse-line line) :region (region-from-fn file))]
;;     (if (and (or (< (Integer/parseInt (:turns data)) 10)
;;                  (< (Integer/parseInt (:points data)) 10))
;;              (or (= (:death data) "quit")
;;                  (= (:death data) "escaped")))
;;       (add-scum (:name data)))
;;     (if (not (is-scum (:name data)))
;;       (let [out (format "[%s] %s, the %s %s %s %s%s %s score was %s."
;;                         (region-from-fn file)
;;                         (:name data)
;;                         (alignment (:align data))
;;                         (gender (:gender data))
;;                         (race (:race data))
;;                         (role (:role data))
;;                         (if (.startsWith (:death data) "ascended")
;;                           (str " " (:death data) " to demigod-hood.")
;;                           (format ", left this world %s on level %s, %s."
;;                                   (zone (:deathdname data))
;;                                   (:deathlev data)
;;                                   (:death data)))
;;                         (possessive-gender (:gender data))
;;                         (:points data))]
;;         (announce irc out)))))

