(ns video-clipping-tool.main
  (:require
   [clojure.tools.cli :as cli]
   [video-clipping-tool.core :as core])
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
    :default false]])


(defn -main
  [& args]
  (let [{:keys [options summary]}  (cli/parse-opts args cli-opts)
        {:keys [clips video output]} options]
    (when-not clips
      (println "Missing clip file. Exiting...")
      (println summary)
      (flush)
      (System/exit -1))
    (core/create-reel video clips output options)))

