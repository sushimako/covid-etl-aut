(ns extract
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as enlive]))
;; Scraping function
;;
(defn load-page [url]
  (do
    (prn "fetching & parsing page")
    (enlive/html-resource (java.net.URL. url))))

(defn load-js [url]
  (letfn [(parse-line [line] [(get-sym line) (get-val line)])
          (get-sym [s] (second (re-find #"([a-zA-Z]+) =" s)))
          (get-val [s] (json/parse-string (second (re-find #"= ([^;]+)$" s))))]
    (->>
      (str/split (slurp url) #";")
      (remove nil?)
      (map parse-line))))
