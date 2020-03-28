(ns scraper
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [java-time :as jt]
            [extract]
            [transform]
            [publish])
  (:gen-class))


(def urls
  {:bmsoz "https://www.sozialministerium.at/Informationen-zum-Coronavirus/Neuartiges-Coronavirus-(2019-nCov).html"
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
  (-> (extract/load-js (:js-simple urls))
      (transform/->timestamp)))


(defn -main [time-range]
  (let [[ts stats]
        (loop [ts (fetch-ts)]
          (if (in-range? time-range ts)
            (let [page (extract/load-page (:bmsoz urls))
                  simple (extract/load-js (:js-simple urls))
                  laender (extract/load-js (:js-laender urls))
                  table (extract/load-sheet)]
              [ts (merge-with merge
                              (transform/cases-at simple)
                              (transform/cases-laender laender)
                              (transform/tests page)
                              (transform/tdouble-at ts table simple)
                              (transform/tdouble-laender ts table laender))])
            (do
              (Thread/sleep (* 30 1000))
              (recur (fetch-ts)))))]
    (pprint stats)
    (publish/update-cells! ts stats)
    (publish/update-time! ts)
    (prn "bye!")))
