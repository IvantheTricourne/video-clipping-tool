(ns video-clipping-tool.util
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as shell]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; general
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn seconds2time
  "Turns a double in seconds into a timestamp ds"
  [s]
  (let [hours (int (Math/floor (/ (/ s 60.0) 60)))
        mins (int (Math/floor (mod (/ s 60.0) 60)))
        sec (int (Math/floor (- (- s (* mins 60)) (* (* hours 60) 60))))]
    {:hour hours
     :minute mins
     :second sec}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; running commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run-command
  "A command is a space separated list"
  [cmd]
  (apply shell/sh cmd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; timestamp
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn better-read-string
  "read-string fails for 08 and 09"
  [number-str]
  (case number-str
    "09" 9
    "08" 8
    (read-string number-str)))

(defn parse-timestamp
  "Parses a timestamp str into a timestamp ds"
  [timestamp-str]
  (let [[h m s] (mapv better-read-string (str/split timestamp-str #":"))]
    {:hour h :minute m :second s}))

(defn timestamp-to-str
  "Turns a timestamp into a string"
  [{:keys [hour minute second]}]
  (->> [hour minute second]
       (mapv #(if (< % 10) (str 0 %) %))
       (str/join ":" )))

(defn add-timestamps
  "Adds timestamps"
  [t1 t2]
  (letfn [(handle-overflow [m-or-s]
            (if (>= m-or-s 60)
              [(- m-or-s 60) inc]
              [m-or-s identity]))]
    (let [[s m-over]
          (handle-overflow (+ (:second t1) (:second t2)))

          [m h-over]
          (handle-overflow (m-over (+ (:minute t1) (:minute t2))))

          h
          (h-over (+ (:hour t1) (:hour t2)))]
      {:hour h :minute m :second s})))

