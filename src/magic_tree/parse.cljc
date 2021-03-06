;; modified from https://github.com/rundis/rewrite-cljs
;; https://github.com/rundis/rewrite-cljs/blob/master/LICENSE

(ns magic-tree.parse
  (:require [magic-tree.reader :as rd]
            [magic-tree.emit :as emit]
            [clojure.tools.reader.reader-types :as r]
            [clojure.tools.reader.edn :as edn]
            [magic-tree.node :as n]
            [clojure.string :as string]))

#?(:cljs (enable-console-print!))

(def ^:dynamic *errors* nil)
(defn error! [info]
  (when (some? *errors*)
    (set! *errors* (conj *errors* info)))
  info)

(def ^:dynamic ^:private *delimiter* nil)
(declare parse-next)
(def non-breaking-space \u00A0)

;; identical? lookups are 10x faster than set-contains and 2x faster than js-array indexOf

(defn ^:boolean newline?
  [c]
  (or (identical? c \newline)
      (identical? c \return)))

(defn ^:boolean space?
  [c]
  (or (identical? c \space)
      (identical? c non-breaking-space)))

(defn ^:boolean whitespace?
  [c]
  (or (identical? c \,)
      (identical? c \space)
      (identical? c non-breaking-space)
      (newline? c)))

(defn ^:boolean boundary?
  [c]
  "Check whether a given char is a token boundary."
  (or (identical? c \")
      (identical? c \:)
      (identical? c \;)
      (identical? c \')
      (identical? c \@)
      (identical? c \^)
      (identical? c \`)
      (identical? c \~)
      (identical? c \()
      (identical? c \))
      (identical? c \[)
      (identical? c \])
      (identical? c \{)
      (identical? c \})
      (identical? c \\)
      (identical? c nil)))

(defn ^:boolean vector-contains?
  [v item]
  (let [end (count v)]
    (loop [i 0]
      (cond (= i end) false
            (identical? (v i) item) true
            :else (recur (inc i))))))

(defn read-to-boundary
  [reader allowed]
  (rd/read-until
    reader
    #(and (or (whitespace? %)
              (boundary? %))
          (not (allowed %)))))

(defn read-to-char-boundary
  [reader]
  (let [c (r/read-char reader)]
    (str c
         (if ^:boolean (not (identical? c \\))
           (read-to-boundary reader #{})
           ""))))

(defn dispatch
  [c]
  (cond (identical? c *delimiter*) :matched-delimiter
        (nil? c) :eof

        (identical? c \,) :comma

        (or (identical? c \space)
            (identical? c non-breaking-space)) :space

        (newline? c) :newline

        (identical? c \^) :meta
        (identical? c \#) :sharp
        (identical? c \() :list
        (identical? c \[) :vector
        (identical? c \{) :map

        (or (identical? c \})
            (identical? c \])
            (identical? c \))) :unmatched-delimiter

        (identical? c \~) :unquote
        (identical? c \') :quote
        (identical? c \`) :syntax-quote
        (identical? c \;) :comment
        (identical? c \@) :deref
        (identical? c \") :string
        (identical? c \:) :keyword
        :else :token))

(defn parse-delim
  [reader delimiter]
  (r/read-char reader)
  (rd/read-repeatedly reader #(binding [*delimiter* delimiter]
                                (parse-next %))))

(defn ^:boolean printable-only? [n]
  (contains? #{:space :comma :newline :comment :comment-block}
             (:tag n)))

(defn parse-printables
  [reader node-tag n & [ignore?]]
  (when-not (nil? ignore?)
    (r/read-char reader))
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
  (let [first-char (r/read-char reader)
        s (->> (if ^:boolean (identical? first-char \\)
                 (read-to-char-boundary reader)
                 (read-to-boundary reader #{}))
               (str first-char))
        ;; determine if string is a symbol, inferring 'yes' on a
        ;; symbol-related read error:
        sexp (try (edn/read-string s)
                  (catch #?(:cljs js/Error
                            :clj Exception) e
                    (when (string/includes? #?(:cljs (ex-message e)
                                               :clj (.getMessage e)) "symbol")
                      ::invalid-symbol)))
        is-symbol ^:boolean (or (symbol? sexp) (= sexp ::invalid-symbol))]
    (if is-symbol
      [:symbol (str s (read-to-boundary reader #{\' \:}))]
      [:token s])))

(defn parse-keyword
  [reader]
  (r/read-char reader)
  (if-let [c (r/peek-char reader)]
    (if ^:boolean (identical? c \:)
      [:namespaced-keyword (edn/read reader)]
      (do (r/unread reader \:)
          [:keyword (edn/read reader)]))
    (rd/throw-reader reader "unexpected EOF while reading keyword.")))

(defn parse-sharp
  [reader]
  (r/read-char reader)
  (case (r/peek-char reader)
    nil (rd/throw-reader reader "Unexpected EOF.")
    \{ [:set (parse-delim reader \})]
    \( [:fn (parse-delim reader \))]
    \" [:regex (rd/read-string-data reader)]
    \^ [:reader-meta (parse-printables reader :reader-meta 2 true)]
    \' [:var (parse-printables reader :var 1 true)]
    \_ [:uneval (parse-printables reader :uneval 1 true)]
    \? (do
         (r/read-char reader)
         (let [read-next #(parse-printables reader :reader-macro 1)
               opts (case (r/peek-char reader)
                      \( {:prefix  "#?"
                          :splice? true}
                      \@ (do (r/read-char reader)
                             {:prefix  "#?@"
                              :splice? true})
                      ;; no idea what this would be, but its \? prefixed
                      (do (rd/unread reader \?)
                          {:prefix (str "#?" (read-next))}))
               value (read-next)]
           [:reader-conditional value opts]))
    [:reader-macro (parse-printables reader :reader-macro 2)]))

(defn parse-unquote
  [reader]
  (r/read-char reader)
  (let [c (r/peek-char reader)]
    (if ^:boolean (identical? c \@)
      [:unquote-splicing (parse-printables reader :unquote 1 true)]
      [:unquote (parse-printables reader :unquote 1)])))

(defn parse-comment-block [reader opening-newline?]
  [:comment-block (loop [text (if opening-newline? \newline "")]
                    (rd/read-while reader #{\;})
                    (when (space? (r/peek-char reader))
                      (r/read-char reader))

                    (let [comment-line (rd/read-until reader #(or (nil? %)
                                                                  (identical? % \newline)
                                                                  (identical? % \return)))
                          next-whitespace (loop [whitespace nil]
                                            (let [next-char (r/peek-char reader)]
                                              (if (whitespace? next-char)
                                                (do (r/read-char reader)
                                                    (recur (str whitespace next-char)))
                                                whitespace)))
                          next-comment? (and next-whitespace (= \; (r/peek-char reader)))]
                      (if next-comment?
                        (recur (str text comment-line next-whitespace))
                        (do
                          (when next-whitespace
                            (doseq [c (reverse next-whitespace)]
                              (r/unread reader c)))
                          (str text comment-line)))))])

(defn parse-next*
  [reader]
  (let [c (r/peek-char reader)
        tag (dispatch c)]
    (case tag
      :token (parse-token reader)
      :keyword (parse-keyword reader)
      :sharp (parse-sharp reader)
      :comment (do (rd/ignore reader)
                   (if (= [0 1] (rd/position reader))
                     (parse-comment-block reader false)
                     [tag (let [content (rd/read-until reader (fn [x] (or (nil? x) (#{\newline \return} x))))]
                            (rd/ignore reader)
                            content)]))
      (:deref
        :quote
        :syntax-quote) [tag (parse-printables reader tag 1 true)]

      :unquote (parse-unquote reader)

      :newline (do (rd/ignore reader)
                   (if (= \; (r/peek-char reader))
                     (parse-comment-block reader true)
                     [tag "\n"]))

      :comma [tag (rd/read-while reader #(identical? % c))]
      :space [tag (rd/read-while reader space?)]
      (:list
        :vector
        :map) [tag (parse-delim reader (get brackets c))]

      :matched-delimiter (do (r/read-char reader) nil)
      (:eof :unmatched-delimiter) (let [the-error (error! [(keyword "error" (name tag)) (let [[line col] (rd/position reader)]
                                                                                          {:position  {:line       line
                                                                                                       :column     col
                                                                                                       :end-line   line
                                                                                                       :end-column (inc col)}
                                                                                           :delimiter *delimiter*})])]
                                    (r/read-char reader)
                                    the-error)
      :meta (do (r/read-char reader)
                [tag (parse-printables reader :meta 2)])
      :string [tag (rd/read-string-data reader)])))

(defn parse-next
  [reader]
  (rd/read-with-position reader parse-next*))

(defn indexing-reader
  "Create reader for strings."
  [s]
  (r/indexing-push-back-reader
    (r/string-push-back-reader s 100)))

(defn ast*
  [s]
  (binding [*errors* []]
    (loop [reader (indexing-reader s)
           values []]
      (if-some [next-thing (rd/read-with-position reader parse-next*)]
        (recur reader (conj values next-thing))
        {:value      values
         :tag        :base
         :errors     *errors*
         :line       0
         :column     0
         :end-line   (r/get-line-number reader)
         :end-column (r/get-column-number reader)}))))

(defn ast
  "Parse ClojureScript source code to AST"
  ([source] (ast nil source))
  ([ns source]
   (let [the-ast (ast* source)
         out-str (emit/string ns the-ast)
         modified-source? (not= source out-str)
         result  (assoc (if modified-source?
                          (ast* out-str)
                          the-ast) :string out-str
                                   :modified-source? modified-source?)]
     result)))