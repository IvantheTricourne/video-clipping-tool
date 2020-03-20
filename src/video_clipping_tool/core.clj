(ns video-clipping-tool.core
	(:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :as shell]))

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
  [timestamp-str]
  (let [[h m s] (mapv better-read-string (str/split timestamp-str #":"))]
    {:hour h :minute m :second s}))

(defn timestamp-to-str
  [{:keys [hour minute second]}]
  (->> [hour minute second]
       (mapv #(if (< % 10) (str 0 %) %))
       (str/join ":" )))

(defn add-timestamps
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; clip file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(comment
  (read-clip-file
   (io/file "/Users/carl/Code/video-clipping-tool/test-files/test.clips"))

  (read-clip-file
   (io/file "/Users/carl/Code/video-clipping-tool/test.clips"))

  
  (+ (read-string "04") (read-string "01"))
  (+ (read-string "10") (read-string "01"))
  (read-string "8")
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; generate ffmpeg commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-clip-command
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
;; run commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- run-command
  "A command is a space separated list"
  [cmd]
  (apply shell/sh cmd))


(comment
  ;; this takes a while
  (def ffmpeg-out
    (run-command (str/split
                  "ffmpeg -i /Users/carl/Code/video-clipping-tool/test.mkv -vf blackdetect=d=0.000001:pix_th=.0001 -f null -"
                  #" ")))

  ;; get black lines
  (def black-detect-lines
    (filterv
     #(str/starts-with? % "[blackdetect")
     (str/split (:err ffmpeg-out) #"\r")))

  (count black-detect-lines) ;; => 21

  ;; get rid of frames
  (def without-frames-printout
    (mapv
     first
     (mapv
      #(str/split % #"\n")
      black-detect-lines)))

  (count without-frames-printout)
  (doseq [bf without-frames-printout]
    (println bf))

  (defn seconds2time [s]
    (let [hours (int (Math/floor (/ (/ s 60.0) 60)))
          mins (int (Math/floor (mod (/ s 60.0) 60)))
          sec (int (Math/floor (- s (* mins 60))))]
      {:hour hours
       :minute mins
       :second sec}))

  (int (Math/floor (mod (/ 125 60.0) 60)))
  (seconds2time 91.533)

  ;; parse into a data structure
  (defn get-blacks [without-frames]
    (let [[start end length]
          (mapv #(read-string (first (str/split % #" ")))
                (rest
                 (str/split
                  without-frames
                  #":")))]
      {:start (timestamp-to-str (seconds2time start))
       :end (timestamp-to-str (seconds2time end))
       :length length}))

  (def timestamps
    (mapv get-blacks without-frames-printout))

  (defn gen-timestamp-file-str
    [timestamps]
    (if (empty? (rest timestamps))
      ""
      (let [{:keys [end]} (first timestamps)
            {:keys [start]} (first (rest timestamps))
            line (format "%s - %s\n" end start)]
        (str line (gen-timestamp-file-str (rest timestamps))))))
  
  (binding [*out* (io/writer (io/file "/Users/carl/Code/video-clipping-tool/test.clips"))]
    (println (gen-timestamp-file-str timestamps)))
  
  )

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
