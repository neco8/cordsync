(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'cordsync)
(def version "1.0.8")
(def class-dir "target/classes")
(def jar-file (format "target/%s.jar" (name lib)))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis (b/create-basis {:project "deps.edn"})
                  :ns-compile '[sync]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file jar-file
           :basis (b/create-basis {:project "deps.edn"})
           :main 'sync}))