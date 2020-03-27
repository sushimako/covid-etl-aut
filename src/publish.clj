(ns publish
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [java-time :as jt :refer [local-date local-date-time]]
            [google-apps-clj.credentials :as gcreds]
            [google-apps-clj.google-sheets :as gsheet]))

(def config (edn/read-string (slurp "etc/config.edn")))
(def scopes [com.google.api.services.drive.DriveScopes/DRIVE])
(defonce creds (gcreds/default-credential scopes))


;; Row/Coll mappings
;;
(defn ts->row-num [ts]
  (+ (jt/time-between (local-date "dd.MM.yyyy" "26.02.2020")
                      (local-date ts)
                      :days)
     2))

(defn col-num
  ([col]
   (get {:time 2} col))
  ([loc col]
   (let [coords {:at                {:tests 3 :cases 4}
                 "Bgld"             {:cases 5}
                 "Burgenland"       {:cases 5}
                 "Ktn"              {:cases 6}
                 "Kärnten"          {:cases 6}
                 "NÖ"               {:cases 7}
                 "Niederösterreich" {:cases 7}
                 "OÖ"               {:cases 8}
                 "Oberösterreich"   {:cases 8}
                 "Sbg"              {:cases 9}
                 "Salzburg"         {:cases 9}
                 "Stmk"             {:cases 10}
                 "Steiermark"       {:cases 10}
                 "T"                {:cases 11}
                 "Tirol"            {:cases 11}
                 "Vbg"              {:cases 12}
                 "Vorarlberg"       {:cases 12}
                 "W"                {:cases 13}
                 "Wien"             {:cases 13}}]
     (get-in coords [loc col]))))


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
