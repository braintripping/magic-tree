(defproject magic-tree "0.0.11"
  :description "Clojure(Script) Source Tool"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.671"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/tools.reader "1.0.2"]
                 [fast-zip "0.7.0"]
                 [cljsjs/codemirror "5.19.0-0"]]

  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-doo "0.1.6"]]

  :lein-release {:deploy-via :clojars}

  :doo {:build "test"
        :paths {:karma "./node_modules/karma/bin/karma"}}

  :cljsbuild {:builds [{:id           "test"
                        :source-paths ["src" "test"]
                        :compiler     {:output-to     "target/test.js"
                                       :output-dir    "target/test"
                                       :main          magic-tree.test-runner
                                       :optimizations :none}}]}

  :source-paths ["src"])
