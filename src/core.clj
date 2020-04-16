(ns core
  (:require [clojure.repl]
            [clojure.edn :as edn]
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
              (transform/all-stats data)
              (transform/tdouble-at data)
              (transform/tdouble-laender data)))

(defn publish-all! [ts stats]
  (let [cutoff (jt/with-offset
                 (jt/adjust ts (jt/local-time 16 30))
                 (jt/zone-offset ts))]
    (prn :ts ts :cutoff cutoff)
    (prn "updating json...")
    (publish/dump-json! ts stats "covid.json")
    (when (before? ts cutoff)
      (prn "updating sheet...")
      (publish/update-cells! ts stats)
      (publish/update-time! ts))))

(defn fetch-ts []
  (-> (load-data :page)
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
