(ns video-clipping-tool.main
  (:require
   [clojure.tools.cli :as cli]
   [video-clipping-tool.core :as core]
   [video-clipping-tool.util :as util]
   [video-clipping-tool.black-detect :as black]
   [clojure.string :as str])
  (:gen-class))

(def cli-opts
  [;; options
   ["-v" "--video VIDEO" "Set video to split/merge"
    :id :video]
   ["-c" "--clips CLIPS" "Set location of clip file"
    :id :clips]
   ["-o" "--output OUTPUT" "Sets output directory location (must use full path)"
    :id :output]
   ["-s" "--split-only" "Does not combine clips."
    :id :split-only
    :default false]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Usage: video-clipping-tool [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  remove-black-frames    Remove black frames from a video"
        "  split                  Split a video with a provided clip file"
        ""
        "Please refer to the manual page for more information."]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (and (= 1 (count arguments))
           (#{"remove-black-frames" "split"} (first arguments)))
      {:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "remove-black-frames"
        (let [black-lines-output
              (do
                (println "Getting black lines... this could take a while.")
                (util/run-command
                 (black/generate-detect-blackframes-command (str (:video options)))))
              clips
              (->> black-lines-output
                   (black/get-black-lines)
                   (mapv black/parse-black-lines)
                   (black/make-parsed-clip-file))]
          (core/create-reel (:video options) clips (:output options) options))
        "split"
        (do
          (when-not (:clips options)
            (println "Missing clip file. Exiting...")
            (flush)
            (System/exit -1))
          (let [clips (core/read-clip-file (:clips options))]
            (core/create-reel (:video options) clips (:output options) options)))))))
