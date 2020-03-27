(ns transform
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [google-apps-clj.credentials :as gcreds]
            [java-time :as jt :refer [local-date local-date-time]]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as enlive]
            [google-apps-clj.google-sheets :as gsheet]
            [extract :as e]
            ))

(defn parse-int [s]
  (->> (str/replace s #"\." "")
       (re-find #"\d+")
       (Integer.)))


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
  (let [ts-str (-> (into {} simple)
                   (get "LetzteAktualisierung"))]
   (local-date-time "dd.MM.yyyy HH:mm.ss" ts-str)))


(defn cases-at [simple]
  {:at {:cases (-> (into {} simple)
                   (get "Erkrankungen"))}})

(defn cases-laender [laender]
  (let [numbers (-> (into {} laender)
                    (get "dpBundesland")
                    (->>
                      (map (juxt #(get % "label") #(get % "y")))))]
    (into {} (map (fn [[k v]] [k {:cases v}]) numbers))))
