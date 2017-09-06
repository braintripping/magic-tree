(ns magic-tree.core
  (:refer-clojure :exclude [range])
  (:require [magic-tree.parse :as parse]
            [magic-tree.emit :as emit]
            [magic-tree.node :as n]
            [magic-tree.nav :as nav]
            [magic-tree.range :as range]
            [fast-zip.core :as z]))

;; Parse

(def ast
  "Given ClojureScript source, returns AST"
  parse/ast)

(defn ast-zip
  "Given AST, returns zipper"
  [ast]
  (z/zipper
    n/may-contain-children?
    :value
    (fn [node children] (assoc node :value children))
    ast))

(def string-zip
  "Given ClojureScript source, returns zipper"
  (comp ast-zip parse/ast))

;; Emit

(def string emit/string)
(def sexp emit/sexp)
(def edges emit/edges)

;; Nodes

(def comment? n/comment?)

(def whitespace? n/whitespace?)
(def newline? n/newline?)
(def sexp? n/sexp?)
(def may-contain-children? n/may-contain-children?)
(def terminal-node? n/terminal-node?)
(def has-edges? n/has-edges?)


;; Navigation

(def child-locs nav/child-locs)
(def right-locs nav/right-locs)
(def left-locs nav/left-locs)
(def top-loc nav/top-loc)
(def closest nav/closest)

(def node-at nav/navigate)
(def mouse-eval-region nav/mouse-eval-region)

;; Ranges

(def within? range/within?)
(def inside? range/inside?)
(def edge-ranges range/edge-ranges)
(def inner-range range/inner-range)
(def bounds range/bounds)

(def empty-range? range/empty-range?)
(def node-highlights range/node-highlights)


(comment

  (def log (atom []))

  (assert (n/within? {:line           1 :column 1
                            :end-line 1 :end-column 2}
                     {:line 1 :column 1}))

  (doseq [[sample-str [line column] result-sexp result-string] [["1" [1 1] 1 "1"]
                                                                ["[1]" [1 1] [1] "[1]"]
                                                                ["#{}" [1 1] #{} "#{}"]
                                                                ["\"\"" [1 1] "" "\"\""]
                                                                ["(+ 1)" [1 0] nil nil]
                                                                ["(+ 1)" [1 1] '(+ 1) "(+ 1)"]
                                                                ["(+ 1)" [1 2] '+ "+"]
                                                                ["(+ 1)" [1 3] nil " "]
                                                                ["(+ 1)" [1 4] 1 "1"]
                                                                ["(+ 1)" [1 5] '(+ 1) "(+ 1)"]
                                                                ["(+ 1)" [1 6] nil nil]
                                                                ["\n1" [2 1] 1 "1"]]]
    (reset! log [])
    (let [result-node (node-at (ast sample-str) {:line   line
                                                 :column column})]
      (assert (= (sexp result-node) result-sexp))
      (assert (= (string result-node) result-string)))))

#_(let [sample-code-string ""]
    (let [_ (.profile js/console "parse-ast")
          ast (time (parse/ast sample-code-string))
          _ (.profileEnd js/console)]
      (println :cljs-core-string-verify (= (emit/string ast) sample-code-string))))