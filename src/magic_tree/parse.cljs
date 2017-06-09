;; modified from https://github.com/rundis/rewrite-cljs
;; https://github.com/rundis/rewrite-cljs/blob/master/LICENSE

(ns magic-tree.parse
  (:require [magic-tree.reader :as rd]
            [magic-tree.emit :as unwrap]
            [cljs.pprint :refer [pprint]]
            [cljs.tools.reader.reader-types :as r]
            [cljs.tools.reader.edn :as edn]
            [cljs.test :refer [is are]]))

(enable-console-print!)

(def ^:dynamic ^:private *delimiter* nil)
(declare parse-next)

(def whitespace-chars #js [\, " " "\n" "\r"])
(defn ^:boolean whitespace?
  [c]
  (< -1 (.indexOf whitespace-chars c)))

(def boundary-chars #js [\" \: \; \' \@ \^ \` \~ \( \) \[ \] \{ \} \\ nil])
(defn ^:boolean boundary?
  [c]
  "Check whether a given char is a token boundary."
  (< -1 (.indexOf boundary-chars c)))

(defn- read-to-boundary
  [reader allowed]
  (rd/read-until
    reader
    #(and (not (< -1 (.indexOf allowed %)))
          (or (whitespace? %)
              (boundary? %)))))

(defn- read-to-char-boundary
  [reader]
  (let [c (rd/next reader)]
    (str c
         (if ^:boolean (not (identical? c \\))
           (read-to-boundary reader #js [])
           ""))))

(defn string->edn
  "Convert string to EDN value."
  [s]
  (edn/read-string s))

(defn- dispatch
  [c]
  (cond (identical? c *delimiter*) :delimiter
        (nil? c) :eof
        :else (case c
                \, :comma
                " " :space
                ("\n" "\r") :newline
                \^ :meta
                \# :sharp
                \( :list
                \[ :vector
                \{ :map
                (\} \] \)) :unmatched
                \~ :unquote
                \' :quote
                \` :syntax-quote
                \; :comment
                \@ :deref
                \" :string
                \: :keyword
                :token)))

(defn- parse-delim
  [reader delimiter]
  (rd/ignore reader)
  (->> #(binding [*delimiter* delimiter]
          (parse-next %))
       (rd/read-repeatedly reader)))

(defn ^:boolean printable-only? [n]
  (contains? #{:space :comma :newline :comment}
             (:tag n)))

(defn- parse-printables
  [reader node-tag n & [ignore?]]
  (when-not (nil? ignore?)
    (rd/ignore reader))
  (rd/read-n
    reader
    node-tag
    parse-next
    (complement printable-only?)
    n))

(def brackets {\( \)
               \[ \]
               \{ \}})

(defn parse-token
  "Parse a single token."
  [reader]
  (let [first-char (rd/next reader)
        s (->> (if ^:boolean (identical? first-char \\)
                 (read-to-char-boundary reader)
                 (read-to-boundary reader #js []))
               (str first-char))]
    [:token (str s (when ^:boolean (symbol? (string->edn s))
                     (read-to-boundary reader #js [\' \:])))]))

(defn parse-keyword
  [reader]
  (rd/ignore reader)
  (if-let [c (rd/peek reader)]
    (if ^:boolean (identical? c \:)
      [:namespaced-keyword (edn/read reader)]
      (do (r/unread reader \:)
          [:keyword (edn/read reader)]))
    (rd/throw-reader reader "unexpected EOF while reading keyword.")))

(defn parse-sharp
  [reader]
  (rd/ignore reader)
  (case (rd/peek reader)
    nil (rd/throw-reader reader "Unexpected EOF.")
    \{ [:set (parse-delim reader \})]
    \( [:fn (parse-delim reader \))]
    \" [:regex (rd/read-string-data reader)]
    \^ [:reader-meta (parse-printables reader :reader-meta 2 true)]
    \' [:var (parse-printables reader :var 1 true)]
    \_ [:uneval (parse-printables reader :uneval 1 true)]
    \? (do
         (rd/next reader)
         (let [read-next #(parse-printables reader :reader-macro 1)
               opts (case (rd/peek reader)
                      \( {:prefix "#?"
                          :splice? true}
                      \@ (do (rd/next reader)
                             {:prefix  "#?@"
                              :splice? true})
                      ;; no idea what this would be, but its \? prefixed
                      (do (rd/unread reader \?)
                          {:prefix (str "#?" (read-next))}))
               value (read-next)]
           [:reader-conditional value opts]))
    [:reader-macro (parse-printables reader :reader-macro 2)]))

(defn- parse-unquote
  [^not-native reader]
  (rd/ignore reader)
  (let [c (rd/peek reader)]
    (if ^:boolean (identical? c \@)
      [:unquote-splicing (parse-printables reader :unquote 1 true)]
      [:unquote (parse-printables reader :unquote 1)])))

(defn parse-next*
  [reader]
  (let [c (rd/peek reader)
        tag (dispatch c)]
    (case tag
      :token (parse-token reader)
      :keyword (parse-keyword reader)
      :sharp (parse-sharp reader)
      :comment (do (rd/ignore reader)
                   [tag (rd/read-until reader (fn [x] (or (nil? x) (#{\newline \return} x))))])
      (:deref
        :quote
        :syntax-quote) [tag (parse-printables reader tag 1 true)]

      :unquote (parse-unquote reader)

      (:newline
        :comma
        :space) [tag (rd/read-while reader #(identical? % c))]
      (:list
        :vector
        :map) [tag (parse-delim reader (get brackets c))]
      :delimiter (rd/ignore reader)
      :unmatched (rd/throw-reader reader "Unmatched delimiter: %s" c)
      :eof (when-not (nil? *delimiter*)
             (rd/throw-reader reader "Unexpected EOF (end of file)"))
      :meta (do (rd/ignore reader)
                [tag (parse-printables reader :meta 2)])
      :string [tag (rd/read-string-data reader)])))

(defn parse-next
  [reader]
  (rd/read-with-position reader parse-next*))

(defn indexing-reader
  "Create reader for strings."
  [s]
  (r/indexing-push-back-reader
    (r/string-push-back-reader s)))

(defn ast
  "Parse ClojureScript source code to AST"
  [s]
  (loop [reader (indexing-reader s)
         values []]
    (if-some [next-thing (rd/read-with-position reader parse-next*)]
      (recur reader (conj values next-thing))
      (merge {:value (vec values)
              :tag   :base}
             (select-keys (last values)
                          [:line :column :end-line :end-column])))))

