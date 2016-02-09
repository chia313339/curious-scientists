(ns curiouser.hungry-dolphins
  (:require [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [incanter.core :as i]
            [incanter.distributions :as d]
            [incanter.io   :as iio]
            [incanter.stats :as s]))

;; This code creates a database, formatted in both JSON and as a
;; CSV. The database is a collection of records and measurements about
;; some fictitious dolphins in a rehabilitation facility.
;;
;; The following fields are stored about each dolphin:
;;   • id :: The database primary key ... unique but not continuous
;;   • name :: A cute name
;;   • species :: A string, like "Bottlenose"
;;   • gender :: A single letter "m" or "f"
;;   • weight :: In kg
;;   • length :: Decimal in meters
;;   • stay :: Random number of days
;;   • lastfed :: A timestamp formatted as YYYYmmddhhmmss
;;
;; Some of the facts and goals for the database include:
;;
;;   • Hundred or so dolphins of various species common to the Caribbean
;;   • Species distribution is appropriate (more Bottlenose than Rizzo's)
;;   • Dolphin length is standard distribution for their species
;;   • Dolphin weight is also SD but slightly lower
;;   • The longer a dolphin stays with the facility to larger their weight
;;   • Most dolphins were fed in the morning, but 1% haven't yet
;;   • Dolphins are given both ID and a name ... appropriate for their sex
;;   • There are more males than females in all species (good question to ask)

;; Each dolphin's weight will be a random sample for its gender and
;; species, but also based on the length it has been in the aquarium.
(def max-length-of-stay 500)

;; To make the IDs somewhat believable and unique, we start with a
;; base number and count up (but skip along the way):
(def last-id (atom (+ (rand-int 200) 1000)))

;; The feeding schedule should also be unique, so we will count up
;; during the day starting at 6 in the morning:
(def last-fed (atom (doto (java.util.Calendar/getInstance)
                      (.set java.util.Calendar/HOUR_OF_DAY 6)
                      (.set java.util.Calendar/MINUTE 0))))

;; Each name is either gender neutral, or could be specifically male
;; or female, and if so, the name is prefixed with the gender:
(def dolphin-names (shuffle ["m:Aftershave"           "Aloha"             "f:Amber"
                             "m:Antelope"           "f:Arial"             "f:Ariel"
                             "f:Audrey"               "Auqa"              "f:Baby"
                             "f:Baby Doll"            "Balloon"             "Banana"
                               "Bashful"            "m:Basketball"          "Blubber"
                               "Blubber Nugget"       "Brandy"              "Brownie"
                               "Bubbles"            "m:Burnie"              "Butterfly"
                               "Cee Cee"            "m:Charles"             "Charlie"
                               "Cheerful"           "f:Chelsea"             "Chi Chi"
                               "Cough"              "f:Crystal"           "f:Diamond"
                             "f:Dolly"                "Donkey"              "Dopey"
                               "Dorsol"               "Empathy"             "Finn"
                               "Finnly"               "Finny"               "Fins"
                               "Flip"                 "Flipa"               "Flippers"
                               "Flips"              "f:Flora"               "Florida"
                               "Freezer"              "Friday"              "Gemini"
                               "Giant"                "Glove"               "Gravy"
                               "Happy"                "Harpo"               "Hawaii"
                               "Headline"             "Heroic"              "Hilo"
                               "Hope"                 "Hovercraft"        "f:Jessica"
                             "m:Jimmy"              "m:Jonny"             "m:Kane"
                               "Kauai"                "Kona"              "m:Lonny"
                               "Luau"                 "Luna"                "Mahalo"
                               "Mandolin"             "Margo"             "m:Marino"
                             "f:Martina"              "Maui"              "f:Mia"
                             "f:Miley"              "f:Milli"             "f:Mimi"
                               "Miracle"              "Molokai"             "Money"
                               "Mopey"                "Nugget"              "Oahu"
                               "Oceania"            "f:Opal"                "Pacific"
                               "Page"                 "Panda"             "m:Panther"
                               "Pants"              "f:Pearl"               "Piano"
                               "Pillow"               "Platano"             "Puppy"
                               "Pyjama"             "f:Rainbow"             "Rutabaga"
                             "f:Samantha"             "Sardine"           "f:Sasha"
                               "Satin"                "Sea Beagle"        "m:Shamoo"
                               "Shazamo"              "Shell"               "Shimmer"
                               "Shimmers"             "Silly"               "Skipper"
                               "Sleepy"               "Slippa"              "Slippy"
                               "Snowball"             "Snowman"             "Sparkle"
                               "Splash"               "Spring"              "Sprinkle"
                               "Squeak"               "Squeaky"             "Squeeks"
                               "Steam"                "Sympathy"            "Syrup"
                             "m:Tenor"                "Tiki"                "Tilly"
                             "m:Timmy"                "Tish"              "m:Tom Tom"
                               "Trial"                "Tribulation"       "m:Triton"
                             "f:Trixie"               "Turkey"              "Turtle"
                               "Volcano"              "Wahini"              "Waitress"
                               "Whipper"              "Whistle"             "Winter"]))

;; Dolphin Types and Species information stored in a CSV:

(defn dolphin-table-row
  "Converts an array of strings from the 'dolphin.csv' to an EDN.
  Do we want to make this a hash map instead of a vector?"
  [row]
  (when (= (count row) 6)
    {:id       (Integer/parseInt (nth row 0))
     :name                       (nth row 1)
     :f-length (Float/parseFloat (nth row 2))
     :m-length (Float/parseFloat (nth row 3))
     :f-weight (Integer/parseInt (nth row 4))
     :m-weight (Integer/parseInt (nth row 5))}))

(def dolphin-table
  "A list of dolphins where each entry is a map of dolphin information from the CSV."
  (with-open [in-file (io/reader "resources/public/data/dolphins/dolphins.csv")]
    (->> in-file
         csv/read-csv
         (drop 1)
         (map dolphin-table-row)
         (filter some?)
         doall)))

;; (:name (first dolphin-table))  => "Bottlenose dolphin"
;; (:name (last dolphin-table))   => "False Killer Whale"

(defn pick-dolphin
  "The dolphin-table's ID does double duty. It also specifies the
  relative amount of that species we should have in our rehabilitation
  park. Since I don't want to make sure the numbers add up to 100, we
  just count the IDs, pick a random number within that 'pool', and
  then re-curse down the table until our random number fits within that
  'slot'."
  []
  (let [pool-size (reduce #(+ %1 (:id %2)) 0 dolphin-table) ;; Sum all IDs
        pool-slot (rand-int pool-size)]

    (loop [pool-counter 0
           dtypes dolphin-table]

      ;; Walk down the dolphin table of types (species)
      (let [dolphin-type (first dtypes)
            new-pool-count (+ pool-counter (:id dolphin-type))]

        ;; Does random number "slot" fits the current count?
        (if (or (not dtypes) (< pool-slot new-pool-count))
          dolphin-type
          (recur new-pool-count (rest dtypes)))))))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))


(defn dolphin-id
  "Generates an ID number between 1000 and 5000, but it is also unique
  within a particular 'run'. This begins with a random number between
  1000 and 1200, and then increments the amount of the next jump
  between 1 and 10."
  []
  (let [incr-fn #(+ % (rand-int 8) 1)]
    (swap! last-id incr-fn)))


(defn dolphin-fed-today
  "Generates the time a dolphin has last been fed. "
  []
  (let [incr-fn #(doto %
                   (.add java.util.Calendar/SECOND
                         (+ (rand-int 200) 100)))]
    (swap! last-fed incr-fn)))

;; (dolphin-fed-today) => #inst "2016-02-07T06:18:37.425-08:00"

(defn dolphin-fed-yesterday
  "Generates a time during the previous day after 5:00."
  []
  (doto (java.util.Calendar/getInstance)
      (.add java.util.Calendar/DAY_OF_MONTH -1)
      (.set java.util.Calendar/HOUR_OF_DAY (+ 15 (rand-int 3)))
      (.set java.util.Calendar/MINUTE (rand-int 59))))

;; (dolphin-fed-yesterday) => #inst "2016-02-07T16:26:02.241-08:00"

(defn dolphin-fed-today-or-yesterday
  "Returns a time of feeding ... mostly this morning, but a few from yesterday."
  []
  (if (> 2 (rand-int 100))
    (dolphin-fed-today)
    (dolphin-fed-yesterday)))

(defn date->str
  "Convert a Calendar entry to be in YYYYMMDDHHMMSS format
   See http://stackoverflow.com/questions/31151589/how-do-you-create-a-time-field-in-alasql"
  [timestamp]
  (.format (java.text.SimpleDateFormat. "yyyyMMddhhmmss") timestamp))

;; (date->str (new java.util.Date)) => "20160208120844"

(defn dolphin-fed
  "The timestamp when the dolphin was fed. Formatted for the SQL engine."
  []
  (date->str (.getTime (dolphin-fed-today-or-yesterday))))

;; (dolphin-fed) => "20160207042357"


(defn dolphin-weight
  "Generates a standard derivation of bottlenose dolphin weight
  between 150 and 650 kg. Each species is different for their gender,
  and also, they are closer to their ideal weight the longer they have
  stayed with the facility."
  [gender species length-of-stay]
  (let [goal   (if (= "m" gender)
                 (:m-weight species)
                 (:f-weight species))
        perct  (inc (/ max-length-of-stay 100))
        offset (/ 30 (inc (/ length-of-stay perct)))
        mean   (- goal (* goal offset))
        sd     (* 0.1 goal)
        distribution (d/normal-distribution goal sd)]
    (int (d/draw distribution))))

;; (dolphin-weight "m" (first dolphin-table) 490)  ;; => 460
;; (dolphin-weight "m" (first dolphin-table) 2)    ;; => 396
;; (dolphin-weight "f" (last  dolphin-table) 250)  ;; => 883

(defn dolphin-length
  "Generates a standard derivation of bottlenose dolphin length between 2 and 4 meters."
  [gender species]
    (let [goal (if (= "f" gender)
               (:f-length species)
               (:m-length species))
        sd   (* 0.1 goal)
        distribution (d/normal-distribution goal sd)]
    (round2 2 (d/draw distribution))))

;; (dolphin-length "f" (first dolphin-table))
;; (dolphin-length "m" (last  dolphin-table))

(defn name-and-gender
  "Return dolphins name and gender based on an initial label."
  [label]
  (let [[gender name] (clojure.string/split label #":")]
    (if name
      [gender name]
      (if (< (rand-int 100) 65)
        ["m" gender]
        ["f" gender]))))

;; (name-and-gender "m:Robert") => ["m" "Robert"]
;; (name-and-gender "f:Lady")   => ["f" "Lady"]
;; (name-and-gender "Bubbles")  => ["m" "Bubbles"]

(defn dolphin
  "Returns a dolphin with a given name and all its specifications as a map."
  [label]
  (let [[gender name] (name-and-gender label)
        species    (pick-dolphin)
        stay-duration (rand-int max-length-of-stay)]
    {:id       (dolphin-id)
     :name      name
     :gender    gender
     :species  (species :name)
     :type     (species :id)
     :stay      stay-duration
     :weight   (dolphin-weight gender species stay-duration)
     :length   (dolphin-length gender species)
     :lastfed  (dolphin-fed)}))

(def dolphins (map dolphin dolphin-names))

;; (first dolphins)
;; (count dolphins)

(with-open [file (io/writer "resources/public/data/dolphins/oasis.json")]
  (json/write dolphins file))

(defn dolphin-edn->vec
  "Converts the dolphin EDN format to a vector suitable for a CSV."
  [d]
  [(:id d)
   (:name d)
   (:species d)
   (:gender d)
   (:weight d)
   (:length d)
   (:stay d)
   (:lastfed d)])

(with-open [out-file (io/writer "resources/public/data/dolphins/oasis.csv")]
  (csv/write-csv out-file
                 (cons ["id" "name" "species" "gender" "weight" "length" "stay" "lastfed"]
                       (map dolphin-edn->vec dolphins))))
