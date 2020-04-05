(ns migrations
  (:require [clojure.repl]
            [clojure.edn :as edn]
            [java-time :as jt]
            [extract]
            [transform]
            [publish]))

(def config (edn/read-string (slurp "etc/config.edn")))
;; calculate & publish tdouble for dates in the past
(comment
  (def tmp (extract/load-sheet (:sheet-id config) (:worksheet-name config)))
  (doseq [row (range 41 40 -1)]
    (prn "Row " row)
    (let [ts (transform/tget tmp row 0)
          stats (for [[loc {:keys [cases]}] (publish/col-num)
                      :let [col (dec cases)
                            value (transform/tget tmp row col)]
                      :when value]
                  [loc {:tdouble (transform/tdouble value ts tmp col)}])]
      (prn :ts ts :stats!! stats)
      (publish/update-cells! ts (into {} stats)))))
