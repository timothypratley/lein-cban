(ns leiningen.cban
  (:require [cban.core :as c]
            [cemerick.pomegranate :as pomegranate]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project :as project]))

(defn load-project-dependencies [project]
  (project/ensure-dynamic-classloader)
  (doseq [path (classpath/get-classpath project)]
    (pomegranate/add-classpath path)))

(defn cban [project & args]
  (let [defaults {:input-dir "resources/cban"
                  :output-dir (:target-path project)}
        {:keys [input-dir output-dir]} (merge defaults (:cban project))]
    (println "CBAN starting...")
    (load-project-dependencies project)
    (c/generate-translations input-dir output-dir)
    (println "CBAN done.")))
