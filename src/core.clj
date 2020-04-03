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
  {:page
   [extract/load-page "https://www.sozialministerium.at/Informationen-zum-Coronavirus/Neuartiges-Coronavirus-(2019-nCov).html"]
   :allgemein
   [extract/load-json "https://github.com/statistikat/coronaDAT/raw/master/latest/allgemein.json"]
   :bundesland
   [extract/load-json "https://github.com/statistikat/coronaDAT/raw/master/latest/bundesland.json"]
   :hospitalisierung
   [extract/load-json "https://github.com/statistikat/coronaDAT/raw/master/latest/hospitalisierungen_bl.json"]
   :table
   [extract/load-sheet (:sheet-id config) (:worksheet-id config)]})

(defn load-all []
  (->> sources
       (pmap (fn [[name [load-fn & args]]]
               [name (apply load-fn args)]))
       (into {})))

(defn load-only [name]
  (when-let [[load-fn & args] (get sources name)]
    {name (apply load-fn args)}))

(defn before? [earlier latter]
  (cond
    (nil? latter) false
    (nil? earlier) true
    :else (> (jt/time-between (jt/local-time earlier)
                              (jt/local-time latter)
                              :seconds)
             0)))

(defn fetch-ts []
  (-> (load-only :allgemein)
      (transform/timestamp)))

(defn publish-all! [ts stats]
  (let [cutoff (jt/local-time 15 30)]
    (prn "publishing new results!")
    (when (before? (jt/local-time ts) cutoff)
      (prn "updating sheet...")
      (publish/update-cells! ts stats)
      (publish/update-time! ts))
    (prn "updating json...")
    (publish/dump-json! ts stats "covid.json")))


(defn transform-all [data]
  (merge-with merge
              (transform/cases-at data)
              (transform/cases-laender data)
              (transform/status-laender data)
              (transform/outcomes-at data)
              (transform/outcomes-laender data)
              (transform/tdouble-at data)
              (transform/tdouble-laender data)))

(defn -main []
  (loop [ts (fetch-ts) last-update nil]
    (if (before? last-update ts)
      (let [stats (-> (load-all)
                      (assoc :ts ts)
                      (transform-all))]
        (publish-all! ts stats)
        (prn "sleeping for 30mins... zZz")
        (Thread/sleep (* 30 60 1000))
        (recur (fetch-ts) ts))
      (do
        (prn "no update, waiting 5mins")
        (Thread/sleep (* 5 60 1000))
        (recur (fetch-ts) last-update))))
  (prn "bye!"))
