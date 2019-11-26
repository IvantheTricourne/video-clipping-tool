(ns video-clipping-tool.core
	(:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.shell :as shell]))

(comment

  (+ 1 1)
  (shell/sh "ls" "-al")

  )

(defn create-reel
  "Top level call"
  [video clips output options])
