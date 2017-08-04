(ns leiningen.cban
  (:require [cban.core :as c]))

(defn cban [{:keys [cban-path cban-target-path] :as project
             :or {cban-path "resources/cban"
                  cban-target-path "cban-out"}}
            & args]
  (println "OK doing it!!!!")
  (c/generate-translations cban-path cban-target-path))
