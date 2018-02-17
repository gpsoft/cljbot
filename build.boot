(def project 'cljbot)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"src"}
          :dependencies   '[[org.clojure/clojure "1.8.0"]])

(task-options!
 aot {:namespace   #{'cljbot.core}}
 pom {:project     project
      :version     version
      :description "UI automation with Robot in Clojure"
      :url         "https://github.com/gpsoft/cljbot"
      :scm         {:url "https://github.com/gpsoft/cljbot"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}}
 jar {:main        'cljbot.core
      :file        (str "cljbot-" version "-standalone.jar")})

(deftask build
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  (let [dir (if (seq dir) dir #{"target"})]
    (comp (aot) (pom) (uber) (jar) (target :dir dir))))

(deftask run
  "Run the project."
  [a args ARG [str] "the arguments for the application."]
  (require '[cljbot.core :as app])
  (apply (resolve 'app/-main) args))


