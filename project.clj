(defproject video-clipping-tool "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.3"]]
  :main video-clipping-tool.main
  :profiles
  {:uberjar {:main video-clipping-tool.main
             :aot  :all}}
  :java-source-paths ["src"]
  :resource-paths ["rsrc"])
