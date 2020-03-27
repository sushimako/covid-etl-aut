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
  (pos? (jt/time-between (jt/local-time earlier) (jt/local-time latter)
                         :seconds)))

(defn -main [time-range]
  (let [time-range "20:00-22:00"
        [tmin tmax] (->> (str/split time-range #"-")
                         (map (partial jt/local-time "H:mm")))
        in-range? #(and (before? tmin %) (before? % tmax))
        fetch-ts #(-> (:js-simple urls)
                     (extract/load-js)
                     (transform/->timestamp))
        [ts stats] (loop [ts (fetch-ts)]
                     (if (in-range? ts)
                       (let [page (extract/load-page (:bmsoz urls))
                             simple (extract/load-js (:js-simple urls))
                             laender (extract/load-js (:js-laender urls))]
                         [ts (merge-with merge
                                         (transform/cases-at simple)
                                         (transform/cases-laender laender)
                                         (transform/tests page))])
                       (do
                         (Thread/sleep (* 30 1000))
                         (recur (fetch-ts)))))]
    (pprint stats)
    (publish/update-cells! ts stats)
    (publish/update-time! ts)
    ;(prn (pr-str {:ts ts :stats stats}))
    (prn "bye!")))
