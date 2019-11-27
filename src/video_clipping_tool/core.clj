(ns video-clipping-tool.core
	(:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :as shell]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; clip file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-timestamp
  [timestamp-str]
  (let [[h m s] (mapv read-string (str/split timestamp-str #":"))]
    {:hour h :minute m :second s}))

(defn timestamp-to-str
  [{:keys [hour minute second]}]
  (->> [hour minute second]
       (mapv #(if (< % 10) (str 0 %) %))
       (str/join ":" )))

(defn- handle-overflow
  [m-or-s]
  (if (>= m-or-s 60)
    [(- m-or-s 60) inc]
    [m-or-s identity]))

(defn add-timestamps
  [t1 t2]
  (let [[s m-over]
        (handle-overflow (+ (:second t1) (:second t2)))

        [m h-over]
        (handle-overflow (m-over (+ (:minute t1) (:minute t2))))

        h
        (h-over (+ (:hour t1) (:hour t2)))]
    {:hour h :minute m :second s}))

(defn make-clip-range
  [clip-range-str]
  (let [split-str (str/split clip-range-str #" ")]
    (cond
      (= (count split-str) 3)
      (let [[t1 op t2] split-str
            p1 (parse-timestamp t1)
            p2 (parse-timestamp t2)]
        (case op
          ;; clip length to range
          "+" [t1 (timestamp-to-str (add-timestamps p1 p2))]
          [t1 t2]))
      :else split-str)))

(defn read-clip-file
  [clips]
  (->> clips
       (slurp)
       (str/split-lines)
       (map-indexed (fn [idx itm] [idx (make-clip-range itm)]))
       (into {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; run commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- run-command
  "A command is a space separated list"
  [cmd]
  (apply shell/sh cmd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; generate ffmpeg commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-clip-command
  "TODO: allow for ranges"
  [input output start-time end-time]
  (-> (format "ffmpeg -i %s -ss %s -to %s -async 1 -strict 2 %s"
              input start-time end-time output)
      (str/split #" ")))

(defn generate-combine-command
  [clip-order-file output-mp4-file]
  (-> (format "ffmpeg -f concat -safe 0 -i %s -c copy %s"
              clip-order-file output-mp4-file)
      (str/split #" ")))

(defn generate-clip
  [idx start-time end-time video output]
  (let [output-file-name (format "clip-%s.mp4" idx)
        output (str (io/file output output-file-name))
        input  (str video)]
    {:cmd (generate-clip-command input output start-time end-time)
     :start start-time
     :end end-time}))

(defn generate-clip-index
  "Associates clips to a specific order based on the clip file"
  [parsed-clip-file video output]
  (->> (for [[idx [start end]] parsed-clip-file]
         [idx (generate-clip idx start end video output)])
       (into {})))

(defn generate-clip-order
  "Clip order is a string of file locations separated by newlines"
  [clip-index]
  (let [stop (count (keys clip-index))]
    (->> (for [i (range stop)]
           (let [{:keys [cmd]} (get clip-index i)]
             (format "file '%s'" (last cmd))))
         (str/join \newline))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; run
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- run-clip-commands
  [clip-index options]
  (let [stop (count (keys clip-index))]
    ;; run commands to generate clip files
    (doseq [i (range stop)]
      (let [{:keys [cmd start end]} (get clip-index i)]
        (println (format "Generating clip (timestamp: %s - %s) ..." start end))
        (run-command cmd)
        (println "Generated clip: " (last cmd))))
    ;; return index
    clip-index))

(defn- run-make-clip-order-file
  [clip-index output {:keys [split-only]}]
  (let [clip-order-file (io/file output "clip-order.txt")]
    ;; write to file
    (when-not split-only
      (binding [*out* (io/writer clip-order-file)]
        (-> clip-index
            (generate-clip-order)
            (println))))
    clip-order-file))

(defn- run-combine-command
  [clip-order-file output-file {:keys [split-only]}]
  (when-not split-only
    (let [cmd (generate-combine-command clip-order-file output-file)]
      (println "Combining clips... this could take a while.")
      (run-command cmd))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; top level call
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-reel
  [video clips output options]
  (-> clips
      (read-clip-file)
      (generate-clip-index video output)
      (run-clip-commands options)
      (run-make-clip-order-file output options)
      (run-combine-command (io/file output "output.mp4") options)))
