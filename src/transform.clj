(ns transform
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [google-apps-clj.credentials :as gcreds]
            [java-time :as jt :refer [local-date zoned-date-time offset-date-time]]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as enlive]
            [google-apps-clj.google-sheets :as gsheet]
            [publish :refer [ts->row-num]]
            [extract :as e]))

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

(defn timestamp [{:keys [allgemein]}]
  (let [ts-str (get (first allgemein) "date")
        tz "CEST"]
    (-> (zoned-date-time "yyyyMMdd_HHmmss z" (str ts-str " " tz))
        (offset-date-time))))


(defn cases-at [{:keys [allgemein]}]
  (let [{:strs [erkrankungen hospitalisiert
                intensivstation nr_tests]} (first allgemein)]
    {:at {:tests nr_tests
          :cases erkrankungen
          :hospital hospitalisiert
          :icu intensivstation}}))

(defn cases-laender [{:keys [bundesland]}]
  (->> bundesland
       (map (fn [{:strs [nuts2 freq]}]
              [(land->kw [:nuts nuts2]) {:cases freq}]))
       (into {})))


(defn status-laender [{:keys [hospitalisierung]}]
  (->> hospitalisierung
       (map (fn [{:strs [nuts2 hospitalisiert intensivstation]}]
              [(land->kw [:nuts nuts2]) {:hospital hospitalisiert
                                      :icu intensivstation}]))
       (into {})))


(defn outcomes-at [{:keys [page]}]
  (let [node (enlive/select page [:#content :> :p])
        node-died (-> node (nth 3) :content (nth 4))
        node-recov (-> node (nth 4) :content (nth 1))]
    {:at {:died (->> node-died (re-find #"[\d\.]+") parse-int)
          :recovered (->> node-recov (re-find #"[\d\.]+") parse-int)}}))

(defn outcomes-laender
  ([data]
   (merge-with merge
               (outcomes-laender data :died)
               (outcomes-laender data :recovered)))
  ([{:keys [page]} outcome]
   (let [node (enlive/select page [:#content :> :p])
         string (case outcome
                  :died (-> node (nth 3) :content (nth 4))
                  :recovered (-> node (nth 4) :content (nth 1)))]
     (->> string
          (re-seq #"(\p{L}+) \(([\d\.]+)\)")
          (map (fn [[_ land val]]
                 [(land->kw land) {outcome (parse-int val)}]))
          (into {})))))

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

(defn tdouble-at [{:keys [ts table allgemein] :as data}]
  (let [value (get-in (cases-at data) [:at :cases])]
    {:at {:tdouble (tdouble value ts table 3)}}))

(defn tdouble-laender [{:keys [ts table bundesland] :as data}]
  (->> (for [[land {:keys [cases]}] (cases-laender data)
             :let [col (publish/col-num land :cases)]]
         [land {:tdouble (tdouble cases ts table (dec col))}])
       (into {})))
