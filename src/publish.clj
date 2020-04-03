(ns publish
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [cheshire.generate :refer [add-encoder]]
            [java-time :as jt :refer [local-date local-date-time]]
            [google-apps-clj.credentials :as gcreds]
            [google-apps-clj.google-sheets :as gsheet]))

(def config (edn/read-string (slurp "etc/config.edn")))
(def scopes [com.google.api.services.drive.DriveScopes/DRIVE])
(defonce creds (gcreds/default-credential scopes))

(add-encoder java.time.LocalDateTime
             (fn [c jsonGenerator]
               (.writeString jsonGenerator (.toString c))))


;; Row/Coll mappings
;;
(defn ts->row-num [ts]
  (+ (jt/time-between (local-date "dd.MM.yyyy" "26.02.2020")
                      (local-date ts)
                      :days)
     3))

(defn col-num
  ([]
   {:at    {:cases 4  :tdouble 5  :recovered 6  :icu 7  :died 8  :tests 3}
    :bgld  {:cases 9  :tdouble 10 :recovered 11 :icu 12 :died 13}
    :ktn   {:cases 14 :tdouble 15 :recovered 16 :icu 17 :died 18}
    :noe   {:cases 19 :tdouble 20 :recovered 21 :icu 22 :died 23}
    :ooe   {:cases 24 :tdouble 25 :recovered 26 :icu 27 :died 28}
    :sbg   {:cases 29 :tdouble 30 :recovered 31 :icu 32 :died 33}
    :stmk  {:cases 34 :tdouble 35 :recovered 36 :icu 37 :died 38}
    :tirol {:cases 39 :tdouble 40 :recovered 41 :icu 42 :died 43}
    :vbg   {:cases 44 :tdouble 45 :recovered 46 :icu 47 :died 48}
    :wien  {:cases 49 :tdouble 50 :recovered 51 :icu 52 :died 53}})
  ([col]
   (get {:time 2} col))
  ([loc col]
   (get-in (col-num) [loc col])))


;; Google sheets helper functions
;;
(defn update-cells! [ts stats]
  (let [{:keys [sheet-id worksheet-id]} config
        cells (for [[loc data] stats
                    [col value] data]
                [(ts->row-num ts) (col-num loc col) (str value)])
        cells (filter (partial every? identity) cells)]
    (prn :saving-cells cells)
    (gsheet/batch-update-cells creds sheet-id worksheet-id cells)))

(defn update-time! [ts]
  (when ts
    (let [{:keys [sheet-id worksheet-id]} config
          row-num (ts->row-num ts)
          col-num (col-num :time)
          value (jt/format "HH:mm" ts)
          cell [row-num col-num value]]
      (gsheet/update-cell creds sheet-id worksheet-id cell))))

(defn dump-json! [ts stats path]
  (->> {:ts ts :stats stats}
       (json/generate-string)
       (spit path)))

; playground
(comment

  (defonce svc (gsheet/build-sheet-service creds))
  (defonce sheet (-> (gsheet/find-spreadsheet-by-id svc sheet-id)
                     :spreadsheet))
  (defonce page (load-page src-url))
  (stats-at page)

  (-> (gsheet/find-worksheet-by-title svc sheet "(src)")
      :worksheet
      (.getId))
  (-> (gsheet/find-spreadsheet-by-id svc "15Uky6rrLP2okIKHXZw-rziuPAEydtk0v9BpV7u53DKM")
      :worksheet))
