(ns leiningen.cban
  (:require [cban.core :as c]
            [cemerick.pomegranate :as pomegranate]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project :as project]
            [leiningen.core.main :as main]))

(defn load-project-dependencies [project]
  (project/ensure-dynamic-classloader)
  (doseq [path (classpath/get-classpath project)]
    (pomegranate/add-classpath path)))

(defn generate-translations [input-dir output-dir output-map-to]
  (let [translation-map (c/get-translation-map input-dir)]
    (c/write-translation-map translation-map output-map-to)
    (c/write-translations translation-map output-dir)))

(defn cban
  "Clojure by anyother name creates translation macros and defs that alias macros and functions,
  so that you can provide your libary in another written language."
  [project & args]
  (let [defaults {:input-dir "translations"
                  :output-dir "cban"
                  :output-map-to (str "resources/" (:name project) "-translations-map.edn")}
        {:keys [input-dir output-dir output-map-to]} (merge defaults (:cban project))]
    (main/info "CBAN starting...")
    (load-project-dependencies project)
    (generate-translations input-dir output-dir output-map-to)
    (main/info "CBAN done.")))
