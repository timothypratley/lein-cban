(ns lein-cban.plugin
  (:require [leiningen.javac :as c]
            [leiningen.cban :as cban]
            [robert.hooke :as h]))

;; TODO: not sure what the right task to hook onto is...
#_(defn hooks []
  (h/add-hook #'c/javac
            (fn [f & args]
              (cban/cban (first args))
              (apply f args))))
