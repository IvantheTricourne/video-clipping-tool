(ns video-clipping-tool.core
	(:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :as shell]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; clip file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-clip-range
  "TODO: allow for ranges and not just direct times"
  [clip-range-str]
  (str/split clip-range-str #" "))

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
