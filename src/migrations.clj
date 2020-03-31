(ns migrations)

;; calculate & publish tdouble for dates in the past
(comment
  (def tmp (extract/load-sheet))
  (doseq [row (range 31 3 -1)]
    (prn "Row " row)
    (let [ts (jt/local-date-time "yyyy-MM-dd HH:mm" (str
                                                      (transform/tget tmp row 0)
                                                      " "
                                                      (transform/tget tmp row 1)))
          stats (for [[loc {:keys [cases]}] (publish/col-num)
                      :let [col (dec cases)
                            value (transform/tget tmp row col)]
                      :when (seq value)]
                  [loc {:tdouble (transform/tdouble
                                   (transform/parse-int value) ts tmp col)}])]
      (prn :ts ts :stats!! stats)
      (publish/update-cells! ts (into {} stats)))))
