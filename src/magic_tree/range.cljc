(ns magic-tree.range
  (:require [magic-tree.emit :as unwrap]
            [magic-tree.node :as n]
            [fast-zip.core :as z]))

(defn contains-fn [include-boundaries?]
  (let [[greater-than less-than] (case include-boundaries?
                  true [>= <=]
                  false [> <])]
    (fn within? [container pos]
      (and container
           (if (map? container)
             (let [{pos-line :line pos-column :column} pos
                   {end-pos-line :end-line end-pos-column :end-column
                    :or   {end-pos-line pos-line
                           end-pos-column pos-column}} pos
                   {:keys [line column end-line end-column]} container]
               (and (>= pos-line line)
                    (<= end-pos-line end-line)
                    (if (= pos-line line) (greater-than pos-column column) true)
                    (if (= end-pos-line end-line) (less-than end-pos-column end-column) true)))
             (within? (z/node container) pos))))))

(defn at-boundary? [node pos])
(def within? (contains-fn true))
(def inside? (contains-fn false))

(defn edge-ranges [node]
  (when (n/has-edges? node)
    (let [[left right] (get unwrap/edges (get node :tag))]
      (cond-> []
              left (conj {:line       (:line node) :end-line (:line node)
                          :column     (:column node)
                          :end-column (+ (:column node) (count left))})
              right (conj {:line       (:end-line node) :end-line (:end-line node)
                           :column     (- (:end-column node) (count right))
                           :end-column (:end-column node)})))))

(defn inner-range [{:keys [line column end-line end-column tag]}]
  (when-let [[left right] (get unwrap/edges tag)]
    {:line       line
     :column     (+ column (count left))
     :end-line   end-line
     :end-column (- end-column (count right))}))

(defn bounds
  "Returns position map for left or right boundary of the node."
  ([node] (select-keys node [:line :column :end-line :end-column]))
  ([node side]
   (case side :left (select-keys node [:line :column])
              :right {:line   (:end-line node)
                      :column (:end-column node)})))

(defn empty-range? [node]
  (and (= (:line node) (:end-line node))
       (= (:column node) (:end-column node))))

(defn node-highlights
  "Get range(s) to highlight for a node. For a collection, only highlight brackets."
  [node]
  (if (n/may-contain-children? node)
    (if (second (get unwrap/edges (get node :tag)))
      (edge-ranges node)
      (update (edge-ranges (first (:value node))) 0 merge (bounds node :left)))
    [node]))