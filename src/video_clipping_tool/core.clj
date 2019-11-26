(ns video-clipping-tool.core
	(:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :as shell]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; clip file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- make-clip-range
  "TODO: allow for ranges and not just direct times"
  [clip-range-str]
  (str/split clip-range-str #" "))

(defn- read-clip-file
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

(defn- generate-clip-command
  [idx start-time end-time video output]
  (let [output-file-name (format "clip-%s.mp4" idx)
        output (str (io/file output output-file-name))
        input  (str video)]
    [idx {:cmd ["ffmpeg" "-i" input "-ss" start-time "-to" end-time "-async" "1" "-strict" "-2" output]
          :start start-time
          :end end-time}]))

(defn- generate-command-map
  "Creates clip commands for generation"
  [parsed-clip-file video output]
  (->> (for [[idx [start end]] parsed-clip-file]
         (generate-clip-command idx start end video output))
       (into {})))

(defn- generate-clip-order-file
  [command-map output]
  (let [stop (count (keys command-map))]
    (->> (for [i (range stop)]
           (let [{:keys [cmd]} (get command-map i)]
             (format "file '%s'" (last cmd))))
         (str/join \newline))))

(defn- generate-combine-command
  [clip-order-file output-mp4-file]
  (let [clip-order-file (str clip-order-file)
        output-mp4-file (str output-mp4-file)]
    ["ffmpeg" "-f" "concat" "-safe" "0" "-i" clip-order-file "-c" "copy" output-mp4-file]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; run
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- run-clip-commands
  [command-map]
  (let [stop (count (keys command-map))]
    (doseq [i (range stop)]
      (let [{:keys [cmd start end]} (get command-map i)]
        (println (format "Generating clip (timestamp: %s - %s) ..." start end))
        (run-command cmd)
        (println "Generated clip: " (last cmd))))
    command-map))

(defn- run-make-clip-order-file
  [command-map output]
  (let [clip-order-str
        (generate-clip-order-file command-map output)

        order-file
        (io/file output "clip-order.txt")]
    (binding [*out* (io/writer order-file)]
      (println clip-order-str))
    order-file))

(defn- run-combine-command
  [clip-order-file output-file]
  (let [cmd (generate-combine-command clip-order-file output-file)]
    (println "Combining clips... this could take a while.")
    (run-command cmd)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; top level call
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-reel
  [video clips output options]
  (let [clip-map
        (read-clip-file clips)

        command-map
        (generate-command-map clip-map video output)]
    (cond-> command-map
      true (run-clip-commands)
      ;; combine clips (or not)
      (not (:split-only options))
      (run-make-clip-order-file output)
      (not (:split-only options))
      (run-combine-command (io/file output "output.mp4")))))
