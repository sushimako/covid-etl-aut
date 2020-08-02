(ns transform
  (:require [clojure.string :as str]
            [java-time :as jt :refer [zoned-date-time offset-date-time]]
            [net.cgrand.enlive-html :as enlive]
            [publish :refer [ts->row-num]]))

(defn parse-int [s]
  (if (seq s)
    (->> (str/replace s #"\." "")
         (re-find #"\d+")
         (Integer.))
    0))

(defn land->kw [land-name]
  (get {"Burgenland"       :bgld
        "Bgld"             :bgld
        [:nuts 1]          :bgld
        "Kärnten"          :ktn
        "Ktn"              :ktn
        [:nuts 2]          :ktn
        "Niederösterreich" :noe
        "NÖ"               :noe
        [:nuts 3]          :noe
        "Oberösterreich"   :ooe
        "OÖ"               :ooe
        [:nuts 4]          :ooe
        "Salzburg"         :sbg
        "Sbg"              :sbg
        [:nuts 5]          :sbg
        "Steiermark"       :stmk
        "Stmk"             :stmk
        [:nuts 6]          :stmk
        "Tirol"            :tirol
        "T"                :tirol
        [:nuts 7]          :tirol
        "Vorarlberg"       :vbg
        "Vbg"              :vbg
        [:nuts 8]          :vbg
        "Wien"             :wien
        "W"                :wien
        [:nuts 9]          :wien}
       land-name))
;;; Transformation & Stats collectors
;;

(defn timestamp [{:keys [page]}]
  (let [ts-str (-> (enlive/select page [:.table :> :tbody
                                        [:tr (enlive/nth-of-type 1)] :th])
                   first
                   :content
                   last)
        date-str (re-find #"\d{2}\.\d{2}\.\d{4}" ts-str)
        time-str (re-find #"\d{2}:\d{2}" ts-str)
        cleaned-ts (str/join " " [date-str time-str "CEST"])]
    (-> (zoned-date-time "dd.MM.yyyy HH:mm z" cleaned-ts)
        (offset-date-time))))

(defn all-stats [{:keys [page]}]
  (let [rows {:cases 1 :died 2 :recovered 3 :hospital 4 :icu 5 :tests 6}
        ks [:bgld :ktn :noe :ooe :sbg :stmk :tirol :vbg :wien :at]
        extract-column (fn [column]
                         (case (count (:content column))
                           1 (or (-> column :content first :content first :content first :content)
                                 (-> column :content first :content first :content)
                                 (-> column :content first :content)
                                 (:content column))
                           2 (or (some-> column :content second :content)
                                 (list  (first (:content column))))
                           3 (-> column :content second :content)))
        parse-row #(->> (enlive/select page [:.table :> :tbody [:tr (enlive/nth-of-type %)] :td])
                        (mapcat extract-column)
                        (map transform/parse-int)
                        (zipmap ks))]
    (apply (partial merge-with merge)
           (for [[kind row] rows
                 [loc n] (parse-row row)]
             {loc {kind n}}))))


;; not using to-array-2d/aget for nil-punning
(defn tget [table row col]
  (some-> (nth table row nil)
          (nth col nil)))

(defn tdouble [value ts table col]
  (let [row (- (ts->row-num ts) 2)
        median (/ value 2)]
    (->>
      (loop [days 0 previous value]
        (let [current (tget table (- row days) col)]
          (if (<= current median)
            (+ days (/ (- median previous)
                       (- current previous)))
            (recur (inc days) current))))
      (float)
      (format "%.1f")
      (Double.))))

(defn tdouble-at [{:keys [ts table] :as data}]
  (let [value (get-in (all-stats data) [:at :cases])]
    {:at {:tdouble (tdouble value ts table 3)}}))

(defn tdouble-laender [{:keys [ts table] :as data}]
  (->> (for [[land {:keys [cases]}] (all-stats data)
             :let [col (publish/col-num land :cases)]]
         [land {:tdouble (tdouble cases ts table (dec col))}])
       (into {})))
