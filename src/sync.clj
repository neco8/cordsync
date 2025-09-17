
(ns sync
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint])
  (:gen-class))

(def cli-options
  [["-c" "--config CONFIG" "Configuration file"
    :default "sync-rules.edn"]
   ["-h" "--help"]])

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn usage [options-summary]
  (str/join
   \newline
   ["Sync values between configuration files"
    ""
    "Usage: cordsync [options]"
    "       cordsync init"
    ""
    "Commands:"
    "  init    Create a sync-rules.edn template"
    ""
    "Options:"
    options-summary]))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  [args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}
      
      errors
      {:exit-message (error-msg errors)}
      
      :else
      {:options options})))

(defn read-json-file [path]
  (try
    (with-open [reader (io/reader path)]
      (json/parse-stream reader true))
    (catch Exception e
      (throw (ex-info (str "Failed to read JSON file: " path) {:path path} e)))))

(defn read-edn-file [path]
  (try
    (with-open [reader (java.io.PushbackReader. (io/reader path))]
      (edn/read reader))
    (catch Exception e
      (throw (ex-info (str "Failed to read EDN file: " path) {:path path} e)))))

(defn read-file [path]
  (try
    (slurp path)
    (catch Exception e
      (throw (ex-info (str "Failed to read file: " path) {:path path} e)))))

(defn write-file [path content]
  (try
    (spit path content)
    (catch Exception e
      (throw (ex-info (str "Failed to write file: " path) {:path path} e)))))

(defn resolve-value [template sources]
  (reduce (fn [result [source-key source-data]]
            (let [pattern (re-pattern (str "\\$\\{" (name source-key) "\\.([^}]+)\\}"))]
              (str/replace result pattern
                          (fn [[_ path]]
                            (str (get-in source-data (map keyword (str/split path #"\."))))))))
          template
          sources))

(defn apply-replacements [content replacements sources]
  (reduce (fn [result {:keys [pattern template]}]
            (let [resolved-template (resolve-value template sources)]
              (str/replace result (re-pattern pattern) resolved-template)))
          content
          replacements))

(defn sync-files! [config]
  (let [{:keys [sources targets]} config
        source-data (reduce (fn [acc [key path]]
                             (assoc acc key (read-json-file path)))
                           {}
                           sources)]
    (doseq [{:keys [file replacements]} targets]
      (let [content (read-file file)
            new-content (apply-replacements content replacements source-data)]
        (when (not= content new-content)
          (write-file file new-content)
          (println (str "Updated: " file)))))))

(defn init-config! []
  (let [template {:sources {:meta "../meta.json"}
                  :targets [{:file "Cargo.toml"
                            :replacements [{:pattern "^name = \".*\""
                                           :template "name = \"${meta.name}\""}
                                          {:pattern "^version = \".*\""
                                           :template "version = \"${meta.version}\""}]}]}]
    (if (.exists (io/file "sync-rules.edn"))
      (do
        (println "sync-rules.edn already exists!")
        (System/exit 1))
      (do
        (spit "sync-rules.edn" (with-out-str (pprint/pprint template)))
        (println "Created sync-rules.edn")))))

(defn -main [& args]
  (cond
    (= (first args) "init")
    (init-config!)
    
    :else
    (let [{:keys [options exit-message ok?]} (validate-args args)]
      (if exit-message
        (exit (if ok? 0 1) exit-message)
        (try
          (let [config-path (:config options)
                config (read-edn-file config-path)]
            (sync-files! config)
            (println "Sync completed successfully"))
          (catch Exception e
            (exit 1 (.getMessage e))))))))