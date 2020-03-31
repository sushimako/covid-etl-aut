(ns transform
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [google-apps-clj.credentials :as gcreds]
            [java-time :as jt :refer [local-date local-date-time]]
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

;;; Transformation & Stats collectors
;;

;; HTML based scraping
;
(defn tests [page]
  (let [node (enlive/select page [:#content :p])]
    {:at
     {:tests (-> node (nth 2) :content second parse-int)}}))


;; transformation of javascript data
;  from info.gesundheitsminiserterium dashboad
(defn ->timestamp [simple]
  (let [ts-str (get simple "LetzteAktualisierung")]
   (local-date-time "dd.MM.yyyy HH:mm.ss" ts-str)))


(defn cases-at [simple]
  {:at {:cases (parse-int (get simple "Erkrankungen"))}})

(defn cases-laender [laender]
  (let [numbers (->> (get laender "dpBundesland")
                     (map (juxt #(get % "label") #(get % "y"))))]
    (into {} (map (fn [[k v]] [k {:cases v}]) numbers))))

;; not using to-array-2d/aget for nil-punning
(defn tget [table row col]
  (some-> (nth table row nil)
          (nth col nil)))

(defn tdouble [value ts table col]
  (let [row (- (ts->row-num ts) 3)
        median (/ value 2)]
    (->>
      (loop [days 0 previous value]
        (let [current (parse-int (tget table (- row days) col))]
          (if (<= current median)
            (+ days (/ (- median previous)
                       (- current previous)))
            (recur (inc days) current))))
      (float)
      (format "%.1f")
      (Double.))))

(defn tdouble-at [ts table simple]
  (let [value (get-in (cases-at simple) [:at :cases])]
    {:at {:tdouble (tdouble value ts table 3)}}))

(defn tdouble-laender [ts table laender]
  (->> (for [[land {:keys [cases]}] (cases-laender laender)
             :let [col (publish/col-num land :cases)]]
         [land {:tdouble (tdouble cases ts table (dec col))}])
       (into {})))
