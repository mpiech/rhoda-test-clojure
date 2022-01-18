(ns rhoda-test-clojure.handler
  (:require
   [nrepl.server :as nrepl]
   [compojure.core :as cpj]
   [compojure.route :as cpjroute]
   [ring.middleware.defaults :as ring]
   [net.cgrand.enlive-html :as enlive]
   [clj-time.core :as time]
   [clj-time.format :as ftime]
   [clj-time.coerce :as ctime]
   [clojure.java.jdbc :as jdbc]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [monger.core :as mg]
   [monger.credentials :as mcr]
   [monger.collection :as mc]
   [monger.operators :refer :all]
   ))

;;;
;;; Static parameters
;;;

(def timezone "America/Los_Angeles")
(def gmaps-key (System/getenv "GMAPS_KEY"))

;;;
;;; SQL database of reservations
;;;

; OLD  MySQL database specs for OpenShift or local
;
;(def dbspec
;  (if-let [host (System/getenv "MYSQL_55_RHEL7_SERVICE_HOST")]
;    {:subprotocol "mysql"
;     :subname (str
;               "//"
;               host
;               ":"
;               (System/getenv "MYSQL_55_RHEL7_SERVICE_PORT")
;               "/testendpoint")
;     :user "testusr"
;     :password "testpwd"
;     }
;    (if-let [host (System/getenv "MYSQL_SERVICE_HOST")]
;      {:subprotocol "mysql"
;       :subname (str
;                 "//"
;                 host
;                 ":"
;                 (System/getenv "MYSQL_SERVICE_PORT")
;                 "/testendpoint")
;       :user "testusr"
;       :password "testpwd"
;       }
;      {:subprotocol "mysql"
;       :subname "//localhost:3306/testendpoint"
;       :user "testusr"
;       :password "testpwd"
;       }
;      )))

; NEW Postgres spec for Crunchy Bridge on OpenShift or local
;                                        
; TODO: get RHODA bindings from injected Service Bindings

(def dbspec
  (if-let [host (System/getenv "PGHOST")]
    {:dbtype "postgresql"
     :dbname (System/getenv "PGDB")
     :host (System/getenv "PGHOST") ; host
     :user (System/getenv "PGUSER")
     :password (System/getenv "PGPASSWORD")
     :ssl true
     :sslmode "require"
     }
    {:subprotocol "mysql"
     :subname "//localhost:3306/testendpoint"
     :user "testusr"
     :password "testpwd"
     }
    ))

;;;
;;; MongoDB of sailing tracks
;;;

; OLD: connection to in-cluster MongoDB
;(def mgconn 
;  (if-let [host (System/getenv "MONGODB_26_RHEL7_SERVICE_HOST")]
;    (let [port (Integer/parseInt
;                (System/getenv
;                 "MONGODB_26_RHEL7_SERVICE_PORT"))
;          uname "testusr"
;          dbname "tracks"
;          pwd-raw "testpwd"
;          pwd (.toCharArray pwd-raw)
;          creds (mcr/create uname dbname pwd)]
;      (mg/connect-with-credentials host port creds))
;    (if-let [host (System/getenv "MONGODB_SERVICE_HOST")]
;      (let [port (Integer/parseInt
;                  (System/getenv
;                   "MONGODB_SERVICE_PORT"))
;            uname "testusr"
;            dbname "tracks"
;            pwd-raw "testpwd"
;            pwd (.toCharArray pwd-raw)
;            creds (mcr/create uname dbname pwd)]
;        (mg/connect-with-credentials host port creds))
;      (mg/connect)
;      )))
;
;(def mgdb (mg/get-db mgconn "mystrk"))

; TODO: get creds from OpenShift Service Binding
;(def atlas-cred-dir "/bindings/cpjdb1-d-cluster0-26b73026be-dbsc")
;(def atlas-username (slurp (str atlas-cred-dir "/username")))
;(def atlas-password (slurp (str atlas-cred-dir "/password")))
;(def atlas-host (slurp (str atlas-cred-dir "/host")))
;(def atlas-db "mystrk")

(def atlas-username (System/getenv "ATLAS_USERNAME"))
(def atlas-password (System/getenv "ATLAS_PASSWORD"))
(def atlas-host (System/getenv "ATLAS_HOST"))
(def atlas-db (System/getenv "ATLAS_DB"))

(def mgdb (:db (mg/connect-via-uri
                (str "mongodb+srv://"
                     atlas-username ":"
                     atlas-password "@"
                     atlas-host "/"
                     atlas-db))))

;;;
;;; Date/Time utilities
;;;

(defn sqldtobj-to-dtobj [sqldtobj]
  (time/to-time-zone
   (ctime/from-sql-time sqldtobj)
   (time/time-zone-for-id timezone)))


;;;
;;; Database read functions
;;;

; reservations and other 'events' from SQL database
; Postgres needs TIMESTAMP cast to make this work, unlike MySQL

(defn db-read-dtobjs [table start-dtstr end-dtstr]
  (let [qstr (str
              "SELECT DISTINCT res_date "
              "FROM " table
              " WHERE CAST (res_date AS TIMESTAMP) >= "
              "CAST ('" start-dtstr "' AS TIMESTAMP) "
              "AND CAST (res_date AS TIMESTAMP) <= "
              "CAST ('" end-dtstr "' AS TIMESTAMP)"
              )]
    (map (fn [x]
           (sqldtobj-to-dtobj (:res_date x)))
         (try
           (jdbc/query dbspec [qstr])
           (catch org.postgresql.util.PSQLException e
             (println qstr (ex-message e)))))
    ))


; sailing tracks from MongoDB
; fix to filter date >= datestr; currently returns all tracks ever

(defn trdb-read-dtobjs [coll start-dtstr end-dtstr]
  (map (fn [x] (:date x))
       (mc/find-maps mgdb coll))
  )

;;;
;;; Enlive - Clojure HTML templating
;;;

;;; for index, simply show index.html with vars replaced

(enlive/deftemplate index "rhoda_test_clojure/index.html"
  []
  [:#gmap] (enlive/replace-vars {:gmapskey gmaps-key})
  )

;;;
;;; Handlers for Compojure - Clojure web app routing
;;;

;;; main calendar page - enlive displays index.html SPA

(defn handler-get-index []
  (index)
  )

(defn handler-get-track [params]
  (let [trdate (get params "date")
        rawTrack (mc/find-one-as-map mgdb "tracks"
                                     {:date trdate})
        ]
    (if rawTrack
      (json/write-str (:points rawTrack))
      (json/write-str '[]))
    )
  )

;;; REST API for AJAX call to get dates as JSON
;;; returns array of e.g. {:title "Mys Rsvd" :start "2021-12-10"}

(defn handler-get-events [params]
  (let [start (subs (get params "start" "2021-12-01") 0 10)
        end (subs (get params "end" "2021-12-31") 0 10)]
    (json/write-str
     (concat
      (map (fn [x]
             {:title "Boat Reserved",
              :start (ftime/unparse
                      (ftime/formatters
                       :date)
                      x)
              })
           (db-read-dtobjs "reservations" start end))
; example of other types of events currently not being captured
;      (map (fn [x]
;             {:title "Bareboat",
;              :start (ftime/unparse
;                      (ftime/formatters
;                       :date)
;                      x)
;              })
;           (db-read-dtobjs "bareboat" start end))
       (map (fn [x]
             {:title "Track",
              :start x
              })
           (trdb-read-dtobjs "tracks" start end))))
    ))

;;; 
;;; Compojure Routing
;;;

(cpj/defroutes app-routes
  (cpj/HEAD "/" [] "")
  (cpj/GET "/" []
    (handler-get-index))
  (cpj/GET "/track" {params :query-params}
    (handler-get-track params))
  (cpj/GET "/events" {params :query-params}
    (handler-get-events params))
  (cpjroute/files "/")
  (cpjroute/resources "/")
  (cpjroute/not-found "Not found.")
  )

;; start nREPL server

(defonce server (nrepl/start-server :port 7888))

;; generated by 'lein ring new'

(def app
  (ring/wrap-defaults app-routes ring/site-defaults)
  )

;;; EOF
