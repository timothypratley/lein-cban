(ns cban.core
  (:require
    [clojure.string :as string]
    [clojure.pprint :as pprint]
    [me.raynes.fs :as fs]
    [semantic-csv.core :as c]
    [clojure.java.io :as io]
    [leiningen.core.main :as main]))

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
(defn defs [source-ns form-translations]
  (for [[existing {:keys [alias docstring]}] form-translations
        :when (and existing alias)
        ;; TODO: read this into the manifest instead
        :let [{:keys [special-form macro]} (meta (ns-resolve (symbol source-ns) (symbol existing)))
              special? (or special-form (contains? special-names existing))]]
    (if (or macro special?)
      (str "(defmacro " alias "\n"
           (when docstring (str "  \"" docstring "\"\n"))
           "  [& body]\n  `(" (when (not special?) (str source-ns "/")) existing " ~@body))")
      (str "(def " alias "\n"
           (when docstring (str "  \"" docstring "\"\n"))
           "  " source-ns "/"
           existing ")"))))

(defn generate-ns [source-ns destination-ns form-translations]
  (require (symbol source-ns))
  (str "(ns cban." destination-ns ")\n\n"
       ";; This file was generated, do not modify it directly\n\n"
       (clojure.string/join "\n\n"
         (defs source-ns form-translations))
       "\n"))

(defn macro? [source-ns x]
  (or (contains? special-names x)
      (let [{:keys [special-form macro]} (meta (ns-resolve (symbol source-ns) (symbol x)))]
        (or special-form macro))))

#_(defn generate-refer [source-ns destination-ns translations]
  (let [alias (for [{:keys [existing alias]} translations
                    :when (and existing alias)]
                alias)]
    (str "(require '[" destination-ns
         " :refer [" (string/join " " (remove #(macro? source-ns %) alias)) "]"
         " :refer-macros [" (string/join " " (filter #(macro? source-ns %) alias)) "]])")))

(defn destination-ns [language source-ns]
  ;; TODO: can we not use dashes?
  (str (string/replace source-ns "." "-")
       "-"
       language))

(defn write-translations [translation-map out-dir]
  (fs/mkdirs (io/file out-dir))
  (doseq [[language namespace-maps] translation-map
          [source-ns form-translations] namespace-maps
          :let [d (destination-ns language source-ns)
                filename (str (string/replace d #"-" "_") ".cljc")
                outfile (io/file out-dir filename)]]
    (spit outfile (generate-ns source-ns d form-translations))
    (main/info "CBAN wrote" (str outfile))))

(defn write-translation-map [translation-map filename]
  (fs/mkdirs (fs/parent filename))
  (spit filename (with-out-str (pprint/pprint translation-map)))
  (main/info "CBAN wrote" filename))

(defn get-namespace-map [file]
  (into {}
        (for [{:keys [existing alias docstring] :as x} (c/slurp-csv file)]
          [existing x])))

(defn get-language [dir language]
  (into {}
        (for [file (fs/list-dir dir)
              :let [source-ns (fs/base-name file ".csv")]]
          [source-ns (get-namespace-map file)])))

(defn get-translation-map [in-dir]
  (into {}
        (for [dir (fs/list-dir in-dir)
              :when (fs/directory? dir)
              :let [language (fs/base-name dir)]]
          [language (get-language dir language)])))

