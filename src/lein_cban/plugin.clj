(ns lein-cban.plugin
  (:require [leiningen.javac :as c]
            [leiningen.cban :as cban]
            [robert.hooke :as h]))

;; TODO: not sure if compile is the right task to hook onto... things like figwheel don't call it?
(defn hooks []
  (h/add-hook #'c/javac
            (fn [f & args]
              (cban/cban (first args))
              (apply f args))))
