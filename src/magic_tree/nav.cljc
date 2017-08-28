(ns magic-tree.nav
  (:refer-clojure :exclude [range])
  (:require [fast-zip.core :as z]
            [magic-tree.node :as n]
            [magic-tree.range :as range]))

(defn child-locs [loc]
  (take-while identity (iterate z/right (z/down loc))))
(defn right-locs [loc]
  (take-while identity (iterate z/right (z/right loc))))
(defn left-locs [loc]
  (take-while identity (iterate z/left (z/left loc))))

(defn navigate
  "Navigate to a position within a zipper (returns loc) or ast (returns node)."
  [ast pos]
  (if (map? ast)
    (when (range/within? ast pos)
      (if
        (or (n/terminal-node? ast) (not (seq (get ast :value))))
        ast
        (or (some-> (filter #(range/within? % pos) (get ast :value))
                    first
                    (navigate pos))
            (when-not (= :base (get ast :tag))
              ast))))
    (let [loc ast
          {:keys [value] :as node} (z/node loc)
          found (when (range/within? node pos)
                  (if
                    (or (n/terminal-node? node) (not (seq value)))
                    loc
                    (or
                      (some-> (filter #(range/within? % pos) (child-locs loc))
                              first
                              (navigate pos))
                      ;; do we want to avoid 'base'?
                      loc #_(when-not (= :base (get node :tag))
                              loc))))]
      (if (let [found-node (some-> found z/node)]
            (and (= (get pos :line) (get found-node :end-line))
                 (= (get pos :column) (get found-node :end-column))))
        (or (z/right found) found)
        found))))

(defn mouse-eval-region
  "Select sexp under the mouse. Whitespace defers to parent."
  [loc]
  (or (and (n/sexp? (z/node loc)) loc)
      (z/up loc)))

(defn top-loc [loc]
  (loop [loc loc]
    (if-not loc
      loc
      (if (= :base (:tag (z/node loc)))
        loc
        (recur (z/up loc))))))