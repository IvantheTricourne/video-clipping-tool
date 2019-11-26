(ns video-clipping-tool.core
	(:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :as shell]))

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

(defn- generate-clip-command
  [idx start-time end-time video output]
  (let [output-file-name (format "clip-%s.mp4" idx)
        output (str (io/file output output-file-name))
        input  (str video)]
    [idx ["ffmpeg" "-i" input "-ss" start-time "-to" end-time "-async" "1" "-strict" "-2" output]]))

(defn- generate-ffmpeg-commands
  [parsed-clip-file video output]
  (->> (for [[idx [start end]] parsed-clip-file]
         (generate-clip-command idx start end video output))
       (into {})))

(defn- run-command
  "A command is a space separated list"
  [cmd]
  (apply shell/sh cmd))

(defn- run-ffmpeg-commands
  [ffmpeg-commands]
  (let [stop (count (keys ffmpeg-commands))]
    (doseq [i (range stop)]
      (let [cmd (get ffmpeg-commands i)]
        (println "Generating mp4...")
        (run-command cmd)
        (println "Generated mp4: " (last cmd))))))

(comment

  (shell/sh "ls" "-al")
  (apply shell/sh ["ls" "-al" "/Users/carl/"])

  ;; gen command
  ["ffmpeg" "-i" video "-ss" start-time "-to" end-time "-async" "1" "-strict" "-2" output]

  (def test-video-file (io/file "/Users/carl/Code/video-clipping-tool/test-video.mp4"))
  (def test-clip-file (io/file "/Users/carl/Code/video-clipping-tool/test.clips"))
  (str test-clip-file)
  (def test-output-dir (io/file "/Users/carl/Code/video-clipping-tool/output/"))

  (def parsed-clip-file (read-clip-file test-clip-file))

  (generate-ffmpeg-commands parsed-clip-file test-video-file test-output-dir)
  )


(defn create-reel
  "Top level call"
  [video clips output options]
  (-> clips
      (read-clip-file)
      (generate-ffmpeg-commands video output)
      (run-ffmpeg-commands)))
