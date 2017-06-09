(ns magic-tree.emit
  (:refer-clojure :exclude [*ns*])
  (:require [cljs.tools.reader.edn :as edn]
            [cljs.tools.reader :as r]
            [fast-zip.core :as z]
            [magic-tree.fn :refer [fn-walk]])
  (:require-macros [magic-tree.backtick :refer [template]]))

(def ^:dynamic *ns* (symbol "magic-tree.user"))
(def ^:dynamic *features* #{:cljs})

(def edges
  {:deref            ["@"]
   :list             [\( \)]
   :fn               ["#(" \)]
   :map              [\{ \}]
   :meta             ["^"]
   :quote            ["'"]
   :reader-meta      ["#^"]
   :raw-meta         ["^"]
   :reader-macro     ["#"]
   :regex            ["#\"" \"]
   :set              ["#{" \}]
   :string           [\" \"]
   :syntax-quote     ["`"]
   :unquote          ["~"]
   :unquote-splicing ["~@"]
   :uneval           ["#_"]
   :var              ["#'"]
   :vector           [\[ \]]
   ;:comment          [";"] ;; to avoid highlighting, we don't consider the leading ; an 'edge'

   })

(def tag-for-print-only? #{:comment :uneval :space :newline :comma})



(declare string)

(defn wrap-children [left right children]
  (str left (apply str (mapv string children)) right))

#_(defn children? [{:keys [tag]}]
    (#{:list :fn :map :meta :set :vector :uneval} tag))

(defn string
  "Emit ClojureScript string from a magic-tree AST"
  ([ns node]
   (binding [*ns* (or ns (symbol "cljs.user"))]
     (string node)))
  ([node]
   (when-not (nil? node)
     (if (map? node)
       (let [{:keys [tag value prefix]} node
             [lbracket rbracket] (get edges tag [])]
         (case tag
           :base (apply str (mapv string value))
           (:token :space :newline :comma) value
           (:deref
             :fn
             :list
             :map
             :quote
             :reader-macro
             :reader-conditional
             :set
             :syntax-quote
             :uneval
             :unquote
             :unquote-splicing
             :var
             :vector) (wrap-children (str lbracket prefix) rbracket value)
           (:meta :reader-meta) (str prefix (wrap-children lbracket rbracket value))
           (:string
             :regex) (str lbracket value rbracket)
           :comment (str ";" value)                         ;; to avoid highlighting, we don't consider the leading ; an 'edge'
           :keyword (str value)
           :namespaced-keyword (str "::" (name value))
           nil ""))
       (string (z/node node))))))

(declare sexp)

(defn as-code [forms]
  (reduce (fn [out {:keys [tag splice?] :as item}]
            (if (tag-for-print-only? tag)
              out
              (let [value (sexp item)]
                ((if splice? into conj)
                  out value)))) [] forms))

(defn sexp [{:keys [tag value prefix] :as node}]
  (when node
    (case tag
      :base (as-code value)

      (:space
        :newline
        :comma) nil

      :string value
      :deref (template (deref ~(first (as-code value))))

      :token (edn/read-string value)
      :vector (vec (as-code value))
      :list (list* (as-code value))
      :fn (fn-walk (as-code value))
      :map (apply hash-map (as-code value))
      :set (template #{~@(as-code value)})
      :var (template #'~(first (as-code value)))
      (:quote :syntax-quote) (template (quote ~(first (as-code value))))
      :unquote (template (~'clojure.core/unquote ~(first (as-code value))))
      :unquote-splicing (template (~'clojure.core/unquote-splicing ~(first (as-code value))))
      :reader-macro (r/read-string (string node))
      :reader-conditional (let [[feature form] (->> (remove #(tag-for-print-only? (:tag %)) (:value (first value)))
                                                    (partition 2)
                                                    (filter (fn [[{feature :value} _]] (contains? *features* feature)))
                                                    (first))]
                            (if feature
                              (cond-> (sexp form)
                                      (= prefix "#?") (vector))
                              []))
      (:meta
        :reader-meta) (let [[m data] (as-code value)]
                        (with-meta data (if (map? m) m {m true})))
      :regex (re-pattern value)
      :namespaced-keyword (keyword *ns* (name value))
      :keyword value

      (:comment
        :uneval) nil)))
