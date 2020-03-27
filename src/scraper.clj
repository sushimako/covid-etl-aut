(ns scraper
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [google-apps-clj.credentials :as gcreds]
            [java-time :as jt :refer [local-date local-date-time]]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as enlive]
            [google-apps-clj.google-sheets :as gsheet])
  (:gen-class))

(def config (edn/read-string (slurp "etc/config.edn")))
;(def src-url "https://www.sozialministerium.at/Informationen-zum-Coronavirus/Neuartiges-Coronavirus-(2019-nCov).html")
;(def sheet-id "15Uky6rrLP2okIKHXZw-rziuPAEydtk0v9BpV7u53DKM")
;(def worksheet-id "odf8qba") ;playground
;(def worksheet-id "om75lgj") ;(src)
(def scopes [com.google.api.services.drive.DriveScopes/DRIVE])
(defonce creds (gcreds/default-credential scopes))
(defonce svc (gsheet/build-sheet-service creds))


(defn parse-int [s]
  (->> (str/replace s #"\." "")
       (re-find #"\d+")
       (Integer.)))


;; Scraping function
;;
(defn load-page [url]
  (do
    (prn "fetching & parsing page")
    (enlive/html-resource (java.net.URL. url))))


;; Transformation & Stats collectors
;;
(defn ->timestamp [page]
  (let [node (enlive/select page [:#content :> :p :> :strong])
        s (-> node first :content first)
        ts-str (str (re-find  #"\d{2}\.\d{2}\.2020" s) " "
                    (re-find  #"\d{2}:\d{2}" s))]
    (local-date-time "dd.MM.yyyy HH:mm" ts-str)))


(defn stats-at [page]
  (let [node (enlive/select page [:#content :> :p :> :strong])]
    {:at
     {:tests (-> node first :content (nth 2) parse-int)
      :cases (-> node second :content first parse-int)
      :recovered (-> node (nth 2) :content first parse-int)
      :died (-> node (nth 2) :content first parse-int)}}))

(defn stats-laender [page]
  (let [content (-> (enlive/select page [:#content :> :div :> :p])
              (nth 3) :content (nth 2))]
    (->> content
         (re-seq #"(\p{L}+) \(([\d\.]+)\)")
         (map (fn [[_ loc value]]
                [loc {:cases (parse-int value)}]))
         (into {}))))


;; Google Sheet ingestion fns
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
                 "Burgenland"       {:cases 5}
                 "Kärnten"          {:cases 6}
                 "Niederösterreich" {:cases 7}
                 "Oberösterreich"   {:cases 8}
                 "Salzburg"         {:cases 9}
                 "Steiermark"       {:cases 10}
                 "Tirol"            {:cases 11}
                 "Vorarlberg"       {:cases 12}
                 "Wien"             {:cases 13}}]
     (prn :col-num loc col (get-in coords [loc col]))
     (get-in coords [loc col]))))

(defn update-cells! [ts stats]
  (let [{:keys [sheet-id worksheet-id]} config
        cells (for [[loc data] stats
                    [col value] data]
                [(ts->row-num ts) (col-num loc col) (str value)])]
    (doseq [cell cells]
      (when (every? identity cell)
        (prn :saving cell)
        (gsheet/update-cell creds sheet-id worksheet-id cell)))))

(defn update-time! [ts]
  (when ts
    (let [{:keys [sheet-id worksheet-id]} config
          row-num (ts->row-num ts)
          col-num (col-num :time)
          value (jt/format "HH:mm" ts)
          cell [row-num col-num value]]
      (gsheet/update-cell creds sheet-id worksheet-id cell))))



;; Main
;;
(defn before? [earlier latter]
  (pos? (jt/time-between (jt/local-time earlier) (jt/local-time latter)
                         :seconds)))

(defn -main [time-range]
  (let [[tmin tmax] (->> (str/split time-range #"-")
                         (map (partial jt/local-time "H:mm")))
        in-range? #(and (before? tmin %) (before? % tmax))
        [ts stats] (reduce (fn [_ page]
                             (if (in-range? (->timestamp page))
                               (reduced [(->timestamp page)
                                         (merge (stats-at page)
                                                (stats-laender page))])
                               (Thread/sleep (* 30 1000))))
                           nil
                           (repeatedly #(load-page (:source-url config))))]
    (update-cells! ts stats)
    (update-time! ts)
    (prn (pr-str {:ts ts :stats stats}))
    (prn "bye!")))



; playground
(comment
  (defonce sheet (-> (gsheet/find-spreadsheet-by-id svc sheet-id)
                     :spreadsheet))
  (defonce page (load-page src-url))
  (stats-at page)

  (-> (gsheet/find-worksheet-by-title svc sheet "(src)")
      :worksheet
      (.getId))
  (-> (gsheet/find-spreadsheet-by-id svc "15Uky6rrLP2okIKHXZw-rziuPAEydtk0v9BpV7u53DKM")
      :worksheet))
