(ns core
  (:require [clojure.repl]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [java-time :as jt]
            [extract]
            [transform]
            [publish])
  (:gen-class))

(def config (edn/read-string (slurp "etc/config.edn")))

(defn before? [earlier latter]
  (cond
    (nil? latter) false
    (nil? earlier) true
    :else (> (jt/time-between earlier latter :seconds) 0)))

(def sources
  {:page
   [extract/load-page "https://www.sozialministerium.at/Informationen-zum-Coronavirus/Neuartiges-Coronavirus-(2019-nCov).html"]
   :allgemein
   [extract/load-json "https://github.com/statistikat/coronaDAT/raw/master/latest/allgemein.json"]
   :bundesland
   [extract/load-json "https://github.com/statistikat/coronaDAT/raw/master/latest/bundesland.json"]
   :hospitalisierung
   [extract/load-json "https://github.com/statistikat/coronaDAT/raw/master/latest/hospitalisierungen_bl.json"]
   :table
   [extract/load-sheet (:sheet-id config) (:worksheet-name config)]})

(defn load-data
  ([]
   (->> sources
        (pmap (fn [[name [load-fn & args]]]
                [name (apply load-fn args)]))
        (into {})))
  ([name]
   (when-let [[load-fn & args] (get sources name)]
     {name (apply load-fn args)})))

(defn transform-all [data]
  (merge-with merge
              (transform/cases-at data)
              (transform/cases-laender data)
              (transform/status-laender data)
              (transform/outcomes-at data)
              (transform/outcomes-laender data)
              (transform/tdouble-at data)
              (transform/tdouble-laender data)))

(defn publish-all! [ts stats]
  (let [cutoff (jt/adjust (jt/offset-date-time) (jt/offset-time 15 30))]
    (prn "updating json...")
    (publish/dump-json! ts stats "covid.json")
    (when (before? ts cutoff)
      (prn "updating sheet...")
      (publish/update-cells! ts stats)
      (publish/update-time! ts))))

(defn fetch-ts []
  (-> (load-data :allgemein)
      (transform/timestamp)))


(defn -main []
  (loop [last-update nil ts (fetch-ts)]
    (if (before? last-update ts)
      (let [stats (-> (load-data)
                      (assoc :ts ts)
                      (transform-all))]
        (publish-all! ts stats)
        (prn "sleeping for 30mins... zZz")
        (Thread/sleep (* 30 60 1000))
        (recur ts (fetch-ts)))
      (do
        (prn "no update, waiting 5mins")
        (Thread/sleep (* 5 60 1000))
        (recur last-update (fetch-ts)))))
  (prn "bye!"))
