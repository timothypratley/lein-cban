(ns cban.core
  (:require
    [clojure.string :as string]
    [me.raynes.fs :as fs]
    [semantic-csv.core :as c]
    [clojure.java.io :as io]))

(def special-names
  #{"def"
    "defn"
    "if"
    "do"
    "let"
    "quote"
    "var"
    "fn"
    "loop"
    "recur"
    "throw"
    "try"
    "monitor-enter"
    "monitor-exit"
    "new"
    "set!"
    "aset"
    "aget"})

;; TODO: looking up meta requires the code as a dependency to be loaded... so can't run as a cmd line
(defn defs [source-ns translations]
  (for [{:keys [existing alias docstring]} translations
        :when (and existing alias)
        :let [{:keys [special-form macro]} (meta (ns-resolve (symbol source-ns) (symbol existing)))
              special? (or special-form (contains? special-names existing))]]
    (if (or macro special?)
      (str "(defmacro " alias "\n"
           (when docstring (str "  \"" docstring "\"\n"))
           "  [& body]\n  `(" (when (not special?) "z/") existing " ~@body))")
      (str "(def " alias "\n"
           (when docstring (str "  \"" docstring "\"\n"))
           "  z/"
           existing ")"))))

(defn generate-ns [source-ns destination-ns translations]
  (str "(ns cban." destination-ns
       "\n  (:require [" source-ns " :as z]))\n\n;; This file was generated, do not modify it directly\n\n"
       (clojure.string/join "\n\n"
         (defs source-ns translations))
       "\n"))

(defn macro? [source-ns x]
  (or (contains? special-names x)
      (let [{:keys [special-form macro]} (meta (ns-resolve (symbol source-ns) (symbol x)))]
        (or special-form macro))))

(defn generate-refer [source-ns destination-ns translations]
  (let [alias (for [{:keys [existing alias]} translations
                    :when (and existing alias)]
                alias)]
    (str "(require '[" destination-ns
         " :refer [" (string/join " " (remove #(macro? source-ns %) alias)) "]"
         " :refer-macros [" (string/join " " (filter #(macro? source-ns %) alias)) "]])")))

(defn generate-translations [in-dir out-dir]
  (doseq [[root dirs files] (fs/iterate-dir in-dir)
          :when (seq files)
          :let [source-ns (fs/base-name root)]
          file files
          :let [translations (c/slurp-csv (io/file root file))
                destination-ns (str (string/replace source-ns "." "-")
                                    "-"
                                    (fs/base-name file ".csv"))]]
    (println "Writing" source-ns files)
    (fs/mkdir (io/file out-dir "cban"))
    (spit (io/file out-dir "cban" (str (string/replace destination-ns #"-" "_") ".cljc"))
          (generate-ns source-ns destination-ns translations))))

(defn generate-refers []
  (doseq [[root dirs files] (fs/iterate-dir "translations")
          :when (seq files)
          :let [source-ns (fs/base-name root)]
          file files
          :let [translations (c/slurp-csv (io/file root file))
                destination-ns (str (string/replace source-ns "." "-")
                                    "-"
                                    (fs/base-name file ".csv"))]]
    (println "Writing" source-ns files)
    (spit (io/file "out" "cban" (str destination-ns ".cljc"))
          (generate-ns source-ns destination-ns translations))
    (spit (io/file "out" "cban" (str destination-ns ".refer"))
          (generate-refer source-ns destination-ns translations))))
