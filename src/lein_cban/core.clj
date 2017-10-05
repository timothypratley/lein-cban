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
  (for [[existing {:keys [alias docstring special-form macro resolved arglists] :as m}] form-translations
        :when (and existing alias)]
    (cond
      (or macro special-form)
      (str "#?(:clj (defmacro " alias "\n"
           (when docstring
             (str "  \"" docstring "\"\n"))
           "  [& body]\n  `("
           (when (and (not special-form)
                      (not= "clojure.core" source-ns))
             "s/")
           existing
           " ~@body)))")

      arglists
      #_(str "(def " alias " ^" (or m {}) "\n"
           #_(when docstring
             (str "  \"" docstring "\"\n"))
           "  "
           (when (not= "clojure.core" source-ns)
             (str source-ns "/"))
           existing
           ")")
      ;; TODO: does not work for nested destructuring (eg assoc-in)... to fix just replace all vectors and maps with an unused letter
      (str "(defn " alias "\n"
           (when docstring
             (str "  \"" docstring "\"\n"))
           (string/join \newline
             (for [args arglists
                   :let [variadic (some #{'&} args)]]
               (str
                 "  ("
                 args
                 "\n   ("
                 (when variadic
                   "apply ")
                 (when (not= "clojure.core" source-ns)
                   "s/")
                 existing
                 (when (seq args)
                   (str
                     " "
                     (string/join " " (remove #{'&} args))))
                 "))")))
           ")")

      :else
      (str "(def " alias "\n"
           (when docstring
             (str "  \"" docstring "\"\n"))
           "  "
           (when (not= "clojure.core" source-ns)
             (str source-ns "/"))
           existing
           ")"))))

(defn maybe-refer [source-ns]
  (try ;; cljs file cannot be loaded by Clojure
    (require (symbol source-ns))
    (catch Exception ex
      (main/warn (str "Failed to require '" source-ns "' - " (.getMessage ex))))))

(defn generate-ns [source-ns destination-ns form-translations]
  (maybe-refer source-ns)
  (str "(ns " root-ns "." destination-ns
       (when (not= "clojure.core" source-ns)
         (str "\n  (:require [" source-ns " :as s])"))
       ")\n\n;; This file was generated, do not modify it directly\n\n"
       (clojure.string/join "\n\n"
         (defs source-ns form-translations))
       "\n"))

(defn destination-ns [language source-ns]
  ;; TODO: can we not use dashes?
  (str (string/replace source-ns "." "-")
       "-"
       language))

(defn write-translations [translation-map output-dir output-extension]
  (fs/mkdirs (io/file output-dir root-ns))
  (doseq [[language namespace-maps] translation-map
          [source-ns form-translations] namespace-maps
          :let [d (destination-ns language source-ns)
                filename (str (string/replace d #"-" "_") "." output-extension)
                outfile (io/file output-dir root-ns filename)]]
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
              (select-keys [:docstring :macro :special-form :arglists]))]
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
