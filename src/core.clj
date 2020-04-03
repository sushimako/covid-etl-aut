(ns core
  (:require [clojure.repl]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [java-time :as jt]
            [extract]
            [transform]
            [publish])
  (:gen-class))

(def urls
  {:bmsoz "https://www.sozialministerium.at/Informationen-zum-Coronavirus/Neuartiges-Coronavirus-(2019-nCov).html"
   :json-allgemein "https://github.com/statistikat/coronaDAT/raw/master/latest/allgemein.json"
   :json-bundesland "https://github.com/statistikat/coronaDAT/raw/master/latest/bundesland.json"
   :json-hospitalisierung "https://github.com/statistikat/coronaDAT/raw/master/latest/hospitalisierungen_bl.json"
   :js-simple  "https://info.gesundheitsministerium.at/data/SimpleData.js"
   :js-laender "https://info.gesundheitsministerium.at/data/Bundesland.js"})


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
  (-> (extract/load-json (:json-allgemein urls))
      (transform/timestamp)))

(defn -main [time-range]
  (let [[ts stats]
        (loop [ts (fetch-ts)]
          (if (in-range? time-range ts)
            (let [page (extract/load-page (:bmsoz urls))
                  allgemein (extract/load-json (:json-allgemein urls))
                  bundesland (extract/load-json (:json-bundesland urls))
                  hospitalisierung (extract/load-json (:json-hospitalisierung urls))
                  table (extract/load-sheet)]
              [ts (merge-with merge
                              (transform/cases-at allgemein)
                              (transform/cases-laender bundesland)
                              (transform/status-laender hospitalisierung)
                              (transform/outcomes-at page)
                              (transform/outcomes-laender page)
                              (transform/tdouble-at ts table allgemein)
                              (transform/tdouble-laender ts table bundesland))])
            (do
              (Thread/sleep (* 30 1000))
              (recur (fetch-ts)))))]
    ;(pprint stats)
    (publish/update-cells! ts stats)
    (publish/update-time! ts)
    (publish/dump-json! ts stats "covid.json")
    (prn "bye!")))
