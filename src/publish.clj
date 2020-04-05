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

(add-encoder java.time.OffsetDateTime
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
   {:at    {:cases 4  :tdouble 5  :hospital 6  :icu 7  :recovered 8  :died 9  :tests 3}
    :bgld  {:cases 10 :tdouble 11 :hospital 12 :icu 13 :recovered 14 :died 15}
    :ktn   {:cases 16 :tdouble 17 :hospital 18 :icu 19 :recovered 20 :died 21}
    :noe   {:cases 22 :tdouble 23 :hospital 24 :icu 25 :recovered 26 :died 27}
    :ooe   {:cases 28 :tdouble 29 :hospital 30 :icu 31 :recovered 32 :died 33}
    :sbg   {:cases 34 :tdouble 35 :hospital 36 :icu 37 :recovered 38 :died 39}
    :stmk  {:cases 40 :tdouble 41 :hospital 42 :icu 43 :recovered 44 :died 45}
    :tirol {:cases 46 :tdouble 47 :hospital 48 :icu 49 :recovered 50 :died 51}
    :vbg   {:cases 52 :tdouble 53 :hospital 54 :icu 55 :recovered 56 :died 57}
    :wien  {:cases 58 :tdouble 59 :hospital 60 :icu 61 :recovered 62 :died 63}})
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
  (defonce sheet (-> (gsheet/find-spreadsheet-by-id svc "15Uky6rrLP2okIKHXZw-rziuPAEydtk0v9BpV7u53DKM")
                     :spreadsheet))
  (defonce page (load-page src-url))

  (-> (gsheet/find-worksheet-by-title svc sheet "(src)")
      :worksheet
      (.getId))
  (-> (gsheet/find-spreadsheet-by-id svc "15Uky6rrLP2okIKHXZw-rziuPAEydtk0v9BpV7u53DKM")
      :worksheet)

  (defonce svc4 (gsheet4/build-service creds))
  (def sheet-data (gsheet4/get-cell-values svc4 "15Uky6rrLP2okIKHXZw-rziuPAEydtk0v9BpV7u53DKM" ["(src)!A:ZZ"])))
