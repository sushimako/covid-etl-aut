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
(def sources
  [[:page
    extract/load-page
    "https://www.sozialministerium.at/Informationen-zum-Coronavirus/Neuartiges-Coronavirus-(2019-nCov).html"]
   [:allgemein
    extract/load-json
    "https://github.com/statistikat/coronaDAT/raw/master/latest/allgemein.json"]
   [:bundesland
    extract/load-json
    "https://github.com/statistikat/coronaDAT/raw/master/latest/bundesland.json"]
   [:hospitalisierung
    extract/load-json "https://github.com/statistikat/coronaDAT/raw/master/latest/hospitalisierungen_bl.json"]
   [:table
    extract/load-sheet
    (:sheet-id config)
    (:worksheet-id config)]])

(defn load-all [sources]
  (->> sources
       (pmap (fn [[name load-fn & args]]
               [name (apply load-fn args)]))
       (into {})))

(defn load-only [sources name]
  {name
   (let [[_ load-fn & args] (->> sources
                                 (filter #(= name (first %)))
                                 first)]
     (apply load-fn args))})

;; Main
;;
(defn before? [earlier latter]
  ((complement neg?) (jt/time-between (jt/local-time earlier)
                                      (jt/local-time latter)
                                      :seconds)))

(defn in-range? [time-range ts]
  (let [[tmin tmax] (->> (str/split time-range #"-")
                         (map (partial jt/local-time "H:mm")))]
    (and (before? tmin ts)
         (before? ts tmax))))

(defn fetch-ts []
  (-> (load-only sources :allgemein)
      (transform/timestamp)))


(defn -main [time-range]
  (let [cutoff (jt/adjust (jt/local-date-time) (jt/local-time 15 30))
        [ts stats]
        (loop [ts (fetch-ts)]
          (if (in-range? time-range ts)
            (let [data (assoc (load-all sources) :ts ts)]
              [ts (merge-with merge
                              (transform/cases-at data)
                              (transform/cases-laender data)
                              (transform/status-laender data)
                              (transform/outcomes-at data)
                              (transform/outcomes-laender data)
                              (transform/tdouble-at data)
                              (transform/tdouble-laender data))])
            (do
              (Thread/sleep (* 30 1000))
              (recur (fetch-ts)))))]
    (when (before? ts cutoff)
      (publish/update-cells! ts stats)
      (publish/update-time! ts))
    ;(pprint stats)
    (publish/dump-json! ts stats "covid.json")
    (prn "bye!")))
