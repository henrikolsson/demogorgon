(ns demogorgon.test.nh
  (:use [demogorgon.nh] :reload)
  (:use	[demogorgon.util])
  (:use	[demogorgon.config])
  (:use [clojure.test])
  (:use [clojure.tools.logging]))

(deftest can-parse-line
  (is (=
       {:player "foobar" :turns "21269" :starttime "1279293021" :game_action "saved"}
       (parse-line "player=foobar:turns=21269:starttime=1279293021:game_action=saved"))))

(deftest can-make-livelout-out
  (let [input ["player=test4:turns=1:shout=HI"               
               "version=UNH-5.3.0:player=jente:turns=2939:starttime=1424706852:currenttime=1424708066:dnum=3:dname=The Gnomish Mines:dlev=11:maxlvl=11:dlev_name=minend:hp=70:maxhp=71:deaths=0:realtime=703:conduct=0xf80:role=Wiz:race=Vam:gender=Mal:align=Cha:gender0=Mal:align0=Cha:explvl=9:exp=2599:elbereths=64:xplevel=9:exp=2599:mode=normal:gold=95:type=achievements:achieve=0x200:achieve_diff=0x200"
               "version=UNH-5.3.0:player=voiceofreason:turns=19375:starttime=1424512846:currenttime=1424678267:dnum=5:dname=Sokoban:dlev=7:maxlvl=36:dlev_name=soko3:hp=117:maxhp=117:deaths=0:realtime=9058:conduct=0xe80:role=Wiz:race=Hum:gender=Mal:align=Cha:gender0=Mal:align0=Cha:explvl=15:exp=175020:elbereths=371:xplevel=15:exp=175020:mode=normal:gold=5601:type=achievements:achieve=0xe07:achieve_diff=0x4"
               "version=UNH-5.3.0:player=Klaak:turns=829:starttime=1426004056:currenttime=1426004363:dnum=0:dname=The Dungeons of Doom:dlev=2:maxlvl=2:dlev_name=:hp=18:maxhp=18:deaths=0:realtime=60:conduct=0xfd8:role=Wiz:race=Vam:gender=Fem:align=Cha:gender0=Fem:align0=Cha:explvl=2:exp=21:elbereths=0:xplevel=2:exp=21:mode=normal:gold=436:type=boh_explosion:boh_explosion=bag of tricks"]
        expected ["You hear test4's distant rumbling: \"HI\""
                   nil
                   "voiceofreason obtained the Candelabrum of Invocation after 19375 turns."
                   "Klaak's bag of holding exploded from a bag of tricks!"]]
    (doseq [x (range (count input))]
      (is (= (make-livelog-out (parse-line (nth input x)))
             (nth expected x))))))
 

;"version=UNH-5.3.0:player=Haudegen:turns=1:starttime=1424729580:currenttime=1424729581:dnum=0:dname=The Dungeons of Doom:dlev=1:maxlvl=1:dlev_name=:hp=11:maxhp=11:deaths=0:realtime=0:conduct=0xfff:role=Rog:race=Orc:gender=Mal:align=Cha:gender0=Mal:align0=Cha:explvl=1:exp=0:elbereths=0:xplevel=1:exp=0:mode=normal:gold=0:type=started:game_action=started:character= chaotic male orcish Rogue"
;"Haudegen enters the dungeon as a chaotic male orcish Rogue."
