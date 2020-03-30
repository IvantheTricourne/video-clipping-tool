(ns video-clipping-tool.black-detect
  (:require [clojure.string :as str]
            [video-clipping-tool.util :as util]))

(defn generate-detect-blackframes-command
  "Get black frames from a video; reports to `:err` field"
  [path-to-video-str]
  (-> (format "ffmpeg -i %s -vf blackdetect=d=0.000001:pix_th=.0001 -f null -"
              path-to-video-str)
      (str/split #" ")))

(defn get-black-lines
  "Get only black lines from detect command"
  [blackframes-command-out]
  (as-> blackframes-command-out it
    ;; command reports to `:err`
    (:err it)
    ;; split by return chars
    (str/split it #"\r")
    ;; get lines that start with the blackdetect line
    (filterv #(str/starts-with? % "[blackdetect") it)
    ;; filter out the extra frames output
    (mapv first (mapv #(str/split % #"\n") it))))

(defn parse-black-lines
  "Parse black lines into timestamps"
  [black-detect-lines]
  (let [[start end length]
        (mapv #(read-string (first (str/split % #" ")))
              (rest (str/split black-detect-lines #":")))]
    {:start (util/timestamp-to-str (util/seconds2time start))
     :end (util/timestamp-to-str (util/seconds2time end))
     :length length}))

(defn make-parsed-clip-file
  "Creates the output of parsing a clip file from a list of parsed black lines"
  [parsed-black-lines]
  (loop [lines parsed-black-lines
         idx 0
         acc {}]
    (cond
      (empty? (rest lines)) acc
      :else
      (let [{:keys [end]} (first lines)
            {:keys [start]} (first (rest lines))]
        (recur (rest lines)
               (inc idx)
               (assoc acc idx [end start]))))))
