(defproject curiouser "0.1.0-SNAPSHOT"
  :description "Data generator for the Curious Scientist"
  :url "http://geeklet.org/curiouser"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [incanter/incanter "1.5.5"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/data.json "0.2.6"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]]
  :resource-paths ["data"]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler curiouser.handler/app}
  :profiles {:dev {:dependencies
                   [[org.clojure/tools.cli "0.3.1"]
                    [javax.servlet/servlet-api "2.5"]
                    [ring/ring-mock "0.3.0"]]}}
  :jvm-opts ["-Xmx2G"])
