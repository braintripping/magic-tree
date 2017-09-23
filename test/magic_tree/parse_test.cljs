(ns magic-tree.parse-test
  (:require [magic-tree.parse :as parse]
            [magic-tree.core :as tree]
            [clojure.test :refer-macros [deftest is are testing]]
            [fast-zip.core :as z]
            [magic-tree.emit :as emit]))

(def shape tree/shape)

(deftest parse
  (binding [magic-tree.emit/*ns* (symbol "magic-tree.parse-test")]
    (testing "parse and emit"

      (are [string res-sexp]
          (let [tree (parse/ast string)]
            (when res-sexp
              (is (= res-sexp (emit/sexp tree))
                  (str "Correct sexp for: " (subs string 0 30))))
            (is (= string (emit/string tree)))
            (str "Correct emitted string for: " string))

          "1" '[1]
          "prn" '[prn]
          "\"hello\"" '["hello"]
          "" '[]
          ":hello" '[:hello]
          ":a/b" '[:a/b]
          "::wha" '[:magic-tree.parse-test/wha]
          "#(+)" '[#(+)]
          "[1 2 3]\n3 4  5, 9" '[[1 2 3] 3 4 5 9]
          "^:dynamic *thing*" '[^:dynamic *thing*]
          "(f x)" '[(f x)]
          "#{1}" '[#{1}]
          "#^:a {}" '[#^:a {}]
          "#'a" '[#'a]
          "@a" '[(deref a)]
          "#_()" '[#_()]
          "'a" '[(quote a)]
          "'a" '['a]
          "`a" '['a]
          "~a" '[~a]
          "#?(:cljs (+ 1 1))" '[(+ 1 1)]
          "#?@(:cljs (+ 1 1))" '[+ 1 1]
          "#?(:cljs 1 :cljs 2)" '[1]                        ;; only keep first match. probably should throw error for duplicate feature.
          "#?(:clj 1 :cljs 2)" '[2]
          "#?(:clj 1)" '[]

        ;"(defn parse-sharp\n  [reader]\n  (rd/ignore reader)\n  (case (rd/peek reader)\n    nil (rd/throw-reader reader \"Unexpected EOF.\")\n    \\{ {:tag      :set\n        :children (parse-delim reader \\})}\n    \\( {:tag      :fn\n        :children (parse-delim reader \\))}\n    \\\" {:tag   :regex\n        :value (rd/read-string-data reader)}\n    \\^ {:tag      :meta\n        :children (parse-printables reader :meta 2 true)\n        :prefix   \"#^\"}\n    \\' {:tag      :var\n        :children (parse-printables reader :var 1 true)}\n    \\_ {:tag      :uneval\n        :children (parse-printables reader :uneval 1 true)}\n    \\? (do\n         (rd/next reader)\n         {:tag :reader-macro\n          :children\n               (let [read1 (fn [] (parse-printables reader :reader-macro 1))]\n                 (cons (case (rd/peek reader)\n                         ;; the easy case, just emit a token\n                         \\( {:tag    :token\n                             :string \"?\"}\n\n                         ;; the harder case, match \\@, consume it and emit the token\n                         \\@ (do (rd/next reader)\n                                {:tag    :token\n                                 :string \"?@\"})\n                         ;; otherwise no idea what we're reading but its \\? prefixed\n                         (do (rd/unread reader \\?)\n                             (read1)))\n                       (read1)))})\n    {:tag      :reader-macro\n     :children (parse-printables reader :reader-macro 2)}))" nil
        ;"(defview editor\n              :component-did-mount\n              (fn [this {:keys [value read-only? on-mount] :as props}]\n                (let [editor (js/CodeMirror (js/ReactDOM.findDOMNode (v/react-ref this \"editor-container\"))\n                                            (clj->js (cond-> options\n                                                             read-only? (-> (select-keys [:theme :mode :lineWrapping])\n                                                                            (assoc :readOnly \"nocursor\")))))]\n                  (when value (.setValue editor (str value)))\n\n                  (when-not read-only?\n\n                    ;; event handlers are passed in as props with keys like :event/mousedown\n                    (doseq [[event-key f] (filter (fn [[k v]] (= (namespace k) \"event\")) props)]\n                      (.on editor (name event-key) f))\n\n                    (.on editor \"beforeChange\" ignore-self-op)\n\n                    (v/update-state! this assoc :editor editor)\n\n                    (when on-mount (on-mount editor this)))))\n              :component-will-receive-props\n              (fn [this {:keys [value]} {next-value :value}]\n                (when (and next-value (not= next-value value))\n                  (when-let [editor (:editor (v/state this))]\n                    (binding [*self-op* true]\n                      (set-preserve-cursor editor next-value)))))\n              :should-component-update\n              (fn [_ _ state _ prev-state]\n                (not= (dissoc state :editor) (dissoc prev-state :editor)))\n              :render\n              (fn [this props state]\n                [:.h-100 {:ref \"editor-container\"}]))" nil
        ;"(list (ns maria.core\n        (:require\n          [maria.codemirror :as cm]\n          [maria.eval :refer [eval-src]]\n          [maria.walkthrough :refer [walkthrough]]\n          [magic-tree.parse]\n          [maria.html]\n\n          [clojure.set]\n          [clojure.string :as string]\n          [clojure.walk]\n\n          [cljs.spec :include-macros true]\n          [cljs.pprint :refer [pprint]]\n          [re-db.d :as d]\n          [re-view.subscriptions :as subs]\n          [re-view.routing :as routing :refer [router]]\n          [re-view.core :as v :refer-macros [defview]]\n          [goog.object :as gobj]))\n\n      (enable-console-print!)\n\n      ;; to support multiple editors\n      (defonce editor-id \"maria-repl-left-pane\")\n\n      (defonce _ (d/listen! [editor-id :source] #(gobj/set (.-localStorage js/window) editor-id %)))\n\n      (defn display-result [{:keys [value error warnings]}]\n        [:div.bb.b--near-white\n         (cond error [:.pa3.dark-red.ph3.mv2 (str error)]\n               (v/is-react-element? value) (value)\n               :else [:.bg-white.pv2.ph3.mv2 (if (nil? value) \"nil\" (try (with-out-str (prn value))\n                                                                         (catch js/Error e \"error printing result\")))])\n         (when (seq warnings)\n           [:.bg-near-white.pa2.pre.mv2\n            [:.dib.dark-red \"Warnings: \"]\n            (for [warning (distinct (map #(dissoc % :env) warnings))]\n              (str \"\\n\" (with-out-str (pprint warning))))])])\n\n      (defn scroll-bottom [component]\n        (let [el (js/ReactDOM.findDOMNode component)]\n          (set! (.-scrollTop el) (.-scrollHeight el))))\n\n      (defn last-n [n v]\n        (subvec v (max 0 (- (count v) n))))\n\n      (defview result-pane\n                    :component-did-update scroll-bottom\n                    :component-did-mount scroll-bottom\n                    :render\n                    (fn [this]\n                      [:div.h-100.overflow-auto.code\n                       (map display-result (last-n 50 (first (v/children this))))]))\n\n      (defview repl\n                    :subscriptions {:source      (subs/db [editor-id :source])\n                                    :eval-result (subs/db [editor-id :eval-result])}\n                    :component-will-mount\n                    #(d/transact! [[:db/add editor-id :source (gobj/getValueByKeys js/window #js [\"localStorage\" editor-id])]])\n                    :render\n                    (fn [_ _ {:keys [eval-result source]}]\n                      [:.flex.flex-row.h-100\n                       [:.w-50.h-100.bg-solarized-light\n                        (cm/editor {:value         source\n                                    :event/keydown #(when (and (= 13 (.-which %2)) (.-metaKey %2))\n                                                     (when-let [source (or (cm/selection-text %1)\n                                                                           (cm/bracket-text %1))]\n                                                       (d/transact! [[:db/update-attr editor-id :eval-result (fnil conj []) (eval-src source)]])))\n                                    :event/change  #(d/transact! [[:db/add editor-id :source (.getValue %1)]])})]\n                       [:.w-50.h-100\n                        (result-pane eval-result)]]))\n\n      (defview not-found\n                    :render\n                    (fn [] [:div \"We couldn't find this page!\"]))\n\n      (defview layout\n                    :subscriptions {:main-view (router \"/\" repl\n                                                       \"/walkthrough\" walkthrough\n                                                       not-found)}\n                    :render\n                    (fn [_ _ {:keys [main-view]}]\n                      [:div.h-100\n                       [:.w-100.fixed.bottom-0.z-3\n                        [:.dib.center\n                         [:a.dib.pa2.black-70.no-underline.f6.bg-black-05 {:href \"/\"} \"REPL\"]\n                         [:a.dib.pa2.black-70.no-underline.f6.bg-black-05 {:href \"/walkthrough\"} \"Walkthrough\"]]]\n                       (main-view)]))\n\n      (defn main []\n        (v/render-to-dom (layout) \"maria-main\"))\n\n      (main))" nil
        "my:symbol" '[my:symbol]

        )



      (let [regexp-string "#\"[a-z]\""
            tree (parse/ast "#\"[a-z]\"")]
        (is (regexp? (-> tree
                         (emit/sexp)
                         (first)))
            "Regular expression is returned from regex string. (Can't test equality, regex's are never equal.)")
        (is (= regexp-string (emit/string tree))
            "Regexp returns same string"))

      )

    (are [in-string the-shape]
      (is (= (shape (parse/ast in-string)) the-shape))

      "\n" [:newline]
      "\n\n;A" [:newline :comment-block]
      ";A" [:comment-block]
      " ;A" [:space :comment]
      "; a

      ; b" [:comment-block]
      "a/" [:symbol]
      )

    (are [in-string out-string]
      (is (=  #_"\n" out-string (emit/string (parse/ast  #_"\n" in-string))))

      ";A" ";; A"

      ";;; A" ";; A"
      ";;;A" ";; A"
      ";;  A" ";;  A"
      ";  AB" ";;  AB"

      ";A\n1\n2\n3\n4\n5" ";; A\n1\n2\n3\n4\n5"

      ";A\n;2\n3\n4" ";; A\n;; 2\n3\n4"

      "\n;A" "\n;; A"
      "\n;A\n" "\n;; A\n"

      ";; A\n;; \n;; B" ";; A\n\n;; B"

      ";; A \n\n\n;; B\n;; C" ";; A \n\n\n;; B\n;; C"
      ";; # Hi!\n;; This" ";; # Hi!\n;; This"
      )

    (comment
      ;; IN PROGRESS
      (testing "parse broken forms"
        (are [string error-count]
          (let [{:keys [errors] :as tree} (parse/ast string)]

            ;; should return same string
            (is (= string (emit/string tree)))

            ;; should throw rather than emit a sexp
            (when (seq errors)
              (is (thrown? js/Error (emit/sexp tree))))

            (is (= (count (:errors tree)) error-count))

            (str "Correct emitted string for: " string))

          "(a b c" 1

          "[(a b c]" 1

          "(a b c)" 0

          ;"a b c)"

          )))


    (testing "selections"
      (binding [emit/*print-selections* true]
        (let [ast (parse/ast "(+ 1 2 3)")
              root (tree/ast-zip ast)
              root-string (comp emit/string z/root)
              cursor {:tag :cursor}
              select-rights (fn [loc n]
                              (let [[contents num] (loop [rights (z/rights loc)
                                                          out [(z/node loc)]
                                                          i 1]
                                                     (if (= i n)
                                                       [out i]
                                                       (recur (rest rights)
                                                              (conj out (first rights))
                                                              (inc i))))
                                    loc (z/replace loc {:tag   :selection
                                                        :value contents})]
                                (last (take num (iterate (comp z/remove z/right) loc)))
                                ))
              grow-selection-right (fn [loc]
                                     (let [node (-> loc z/right z/node)]
                                       (-> loc
                                           (z/edit update :value conj node)
                                           z/right
                                           z/remove
                                           z/up)))]

          (are [x y] (= x y)

                     (-> (z/down root)
                         (z/insert-right cursor)
                         (root-string)) "(+ 1 2 3)|"

                     (-> (z/down root)
                         (z/down)
                         (z/insert-right cursor)
                         (root-string)) "(+| 1 2 3)"

                     (-> root
                         z/down
                         z/down
                         (select-rights 2)
                         (root-string)) "(‹+ ›1 2 3)"

                     (-> root
                         z/down
                         z/down
                         (z/insert-left {:tag   :selection
                                         :value []})
                         z/left
                         (grow-selection-right)
                         (grow-selection-right)
                         (grow-selection-right)
                         root-string) "(‹+ 1› 2 3)"

                     ;;;; next
                     ;; - select by row/col
                     ;; - better selection navigation. eg/ provide

                     )

          )))

    ))


