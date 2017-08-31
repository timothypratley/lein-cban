(ns lein-cban.core
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
    "true"
    "false"
    "monitor-enter"
    "monitor-exit"
    "new"
    "set!"
    "aset"
    "aget"})

(def root-ns
  "cban")

(defn defs [source-ns form-translations]
  ;; TODO: make use of the argslist
  (for [[existing {:keys [alias docstring special-form macro resolved argslist]}] form-translations
        :when (and existing alias)]
    (if (or macro special-form)
      (str "(defmacro " alias "\n"
           (when docstring (str "  \"" docstring "\"\n"))
           "  [& body]\n  `(" (when (not special-form) (str source-ns "/")) existing " ~@body))")
      (str "(def " alias "\n"
           (when docstring (str "  \"" docstring "\"\n"))
           "  " source-ns "/"
           existing ")"))))

(defn maybe-refer [source-ns]
  (try ;; cljs file cannot be loaded by Clojure
    (require (symbol source-ns))
    (catch Exception ex
      (main/warn (str "Failed to require '" source-ns "' - " (.getMessage ex))))))

(defn generate-ns [source-ns destination-ns form-translations]
  (maybe-refer source-ns)
  (str "(ns " root-ns "." destination-ns ")\n\n"
       ";; This file was generated, do not modify it directly\n\n"
       (clojure.string/join "\n\n"
         (defs source-ns form-translations))
       "\n"))

(defn destination-ns [language source-ns]
  ;; TODO: can we not use dashes?
  (str (string/replace source-ns "." "-")
       "-"
       language))

(defn write-translations [translation-map out-dir]
  (fs/mkdirs (io/file out-dir root-ns))
  (doseq [[language namespace-maps] translation-map
          [source-ns form-translations] namespace-maps
          :let [d (destination-ns language source-ns)
                filename (str (string/replace d #"-" "_") ".cljc")
                outfile (io/file out-dir root-ns filename)]]
    (spit outfile (generate-ns source-ns d form-translations))
    (main/info "CBAN wrote" (str outfile))
    (try
      (main/info "CBAN loaded" (load-file (str outfile)))
      (catch Exception ex
        (main/warn "CBAN FAILED to load" (str outfile) (str ex))))))

(defn write-translation-map [translation-map filename]
  (fs/mkdirs (fs/parent filename))
  (spit filename (with-out-str (pprint/pprint translation-map)))
  (main/info "CBAN wrote" filename))

(defn maybe-resolve [source-ns existing]
  (let [v (try
            (ns-resolve (symbol source-ns) (symbol existing))
            (catch Exception ex
              (main/warn (str "Failed to resolve '" source-ns "/" existing "' - " (.getMessage ex)))))
        m (-> v
              (meta)
              (select-keys [:docstring :macro :special-form :argslist]))]
    (cond-> m
      v (assoc :resolved true)
      (contains? special-names existing) (assoc :special-form true))))

(defn get-namespace-map [file source-ns]
  (maybe-refer source-ns)
  (into (sorted-map)
        (for [{:keys [existing alias docstring] :as row} (c/slurp-csv file)
              :let [metadata (maybe-resolve source-ns existing)]]
          [existing (merge metadata row)])))

(defn get-language [dir language]
  (into (sorted-map)
        (for [file (fs/list-dir dir)
              :let [source-ns (fs/base-name file ".csv")]]
          [source-ns (get-namespace-map file source-ns)])))

(defn get-translation-map [in-dir]
  (into (sorted-map)
        (for [dir (fs/list-dir in-dir)
              :when (fs/directory? dir)
              :let [language (fs/base-name dir)]]
          [language (get-language dir language)])))
