(ns magic-tree-codemirror.edit
  (:require [cljsjs.codemirror]
            [cljsjs.codemirror.addon.search.searchcursor]
            [magic-tree.core :as tree]
            [magic-tree-codemirror.util :as cm-util]
            [fast-zip.core :as z]
            [goog.dom :as dom]))


(aset js/window "onload"
      #(defonce paste-element (let [textarea (doto (dom/createElement "input")
                                               (dom/setProperties #js {:id        "magic-tree.pasteHelper"
                                                                       :className "fixed o-0 z-0 bottom-0 right-0"}))]
                                (dom/appendChild js/document.body textarea)
                                textarea)))

(def pass (.-Pass js/CodeMirror))

(defn copy
  "Copy text to clipboard using a hidden input element."
  [text]
  (let [hadFocus (.-activeElement js/document)]
    (doto paste-element
      (.setAttribute "value" text)
      (.select))
    (try (.execCommand js/document "copy")
         (catch js/Error e (.error js/console "Copy command didn't work. Maybe a browser incompatibility?")))
    (.focus hadFocus)))

(defn cm-pos
  "Return a javascript object with `line` and `ch` keys, for CodeMirror. Prefer to pass around
  Clojure {:line .. :column ..} maps until performing an action on the editor."
  [{:keys [line column]}]
  #js {:line line :ch column})

(defn replace-range
  ([cm s from {:keys [line column]}]
   (replace-range cm s (merge from {:end-line line :end-column column})))
  ([cm s {:keys [line column end-line end-column]}]
   (.replaceRange cm s
                  #js {:line line :ch column}
                  #js {:line (or end-line line) :ch (or end-column column)})))

(defn cut-range
  "Cut a {:line .. :column ..} range from a CodeMirror instance."
  [cm range]
  (let [[from to] (cm-util/parse-range range)]
    (copy (.getRange cm from to))
    (.replaceRange cm "" from to)))

(defn copy-range
  "Copy a {:line .. :column ..} range from a CodeMirror instance."
  [cm range]
  (let [[from to] (cm-util/parse-range range)]
    (copy (.getRange cm from to))))

(defn select-range
  "Copy a {:line .. :column ..} range from a CodeMirror instance."
  [cm range]
  (let [[from to] (cm-util/parse-range range)]
    (.setSelection cm from to #js {:scroll false})))

(defn selection-boundaries
  [cm]
  (if (.somethingSelected cm)
    (let [sel (first (.listSelections cm))
          from (.from sel)
          to (.to sel)]
      {:line       (.-line from)
       :column     (.-ch from)
       :end-line   (.-line to)
       :end-column (.-ch to)})
    (let [cur (.getCursor cm)]
      {:line       (.-line cur)
       :column     (.-ch cur)
       :end-line   (.-line cur)
       :end-column (.-ch cur)})))

(defn get-kill-range
  "Returns range beginning at cursor, ending at newline or inner boundary of current node."
  [pos loc]
  (let [start-pos (select-keys pos [:line :column])]
    (merge start-pos
           (if (and (= :string (get (z/node loc) :tag))
                    (not= start-pos (tree/boundaries (z/node loc) :left)))
             (-> (select-keys (z/node loc) [:end-line :end-column])
                 (update :end-column dec))
             (let [locs-to-delete (->> (cons loc (tree/right-locs loc))
                                       (take-while (comp not tree/newline? z/node)))]
               (select-keys (some-> (or (last locs-to-delete) loc) z/node) [:end-line :end-column]))))))

(defn cursor-boundary-skip
  "Returns function for moving cursor left or right, touching only node boundaries."
  [side]
  (fn [{{:keys [pos loc]} :magic/cursor :as cm}]
    (let [next-loc (case side :left z/left :right z/right)]
      (loop [loc loc]
        (cond (not= pos (tree/boundaries (z/node loc) side)) (.setCursor cm (cm-pos (tree/boundaries (z/node loc) side)))
              (next-loc loc) (recur (next-loc loc))
              :else (some->> (z/up loc) recur))))))

(defn pos= [p1 p2]
  (= (tree/boundaries p1)
     (tree/boundaries p2)))

(defn move-char [cm pos amount]
  (.findPosH cm pos amount "char" false))

(defn char-at [cm pos]
  (.getRange cm pos (move-char cm pos 1)))

(def commands {:kill
               (fn [{{pos :pos loc :loc} :magic/cursor :as cm}]
                 (if (.somethingSelected cm)
                   pass
                   (->> (get-kill-range pos loc)
                        (cut-range cm))))

               #_:delete
               ;; early, awkward attempt at handling brackets
               #_(fn [cm]
                   (if (.somethingSelected cm)
                     pass
                     (doseq [selection (reverse (.listSelections cm))]
                       (let [pos (move-char cm (.-head selection) -1)
                             char (char-at cm pos)]
                         (do (when-not (#{\) \] \}} char)
                               (.replaceRange cm "" (.-head selection) pos))
                             #js {"anchor" (.-head selection)
                                  "head"   (.-head selection)})

                         ))))

               :copy-at-point
               (fn [cm] (if (.somethingSelected cm)
                          pass
                          (copy-range cm (get-in cm [:magic/cursor :bracket-node]))))

               :cut-at-point
               (fn [cm] (if (.somethingSelected cm)
                          pass
                          (cut-range cm (get-in cm [:magic/cursor :bracket-node]))))

               :delete-at-point
               (fn [cm] (if (.somethingSelected cm)
                          pass
                          (replace-range cm "" (get-in cm [:magic/cursor :bracket-node]))))

               :hop-left
               (cursor-boundary-skip :left)

               :hop-right
               (cursor-boundary-skip :right)

               :expand-selection
               (fn [{{:keys [bracket-node] cursor-pos :pos} :magic/cursor
                     zipper                                 :zipper
                     :as                                    cm}]
                 (let [sel (selection-boundaries cm)
                       loc (tree/node-at zipper sel)
                       node (z/node loc)
                       parent (some-> (z/up loc) z/node)
                       select #(when %
                                 (select-range cm %)
                                 (swap! cm update-in [:magic/cursor :stack] conj (tree/boundaries %)))]
                   (cond
                     (not (.somethingSelected cm))
                     (do (swap! cm assoc-in [:magic/cursor :stack] (list (selection-boundaries cm)))
                         (.magicClearHighlight cm)
                         (select (let [node (if (tree/comment? node) node bracket-node)]
                                   (or (when (tree/inside? node cursor-pos) (tree/inner-range node))
                                       node))))
                     (= sel (tree/inner-range parent)) (select parent)
                     (pos= sel (tree/boundaries node)) (select (or (tree/inner-range parent) parent))
                     (= sel (tree/inner-range node)) (select node)
                     :else nil)))

               :shrink-selection
               (fn [cm]
                 (when-let [stack (get-in cm [:magic/cursor :stack])]
                   (let [stack (cond-> stack
                                       (or (:base (first stack))
                                           (= (selection-boundaries cm) (first stack))) rest)]
                     (some->> (first stack) (select-range cm))
                     (swap! cm update-in [:magic/cursor :stack] rest))))

               :comment-line
               (fn [{zipper :zipper :as cm}]
                 (let [{line-n :line column-n :column} (get-in cm [:magic/sursor :pos])
                       [spaces semicolons] (rest (re-find #"^(\s*)(;+)?" (.getLine cm line-n)))
                       [space-n semicolon-n] (map count [spaces semicolons])]
                   (if (> semicolon-n 0)
                     (replace-range cm "" {:line line-n :column space-n :end-column (+ space-n semicolon-n)})
                     (let [{:keys [end-line end-column]} (some-> (tree/node-at zipper {:line line-n :column 0})
                                                                 z/up
                                                                 z/node)]
                       (when (= line-n end-line)
                         (replace-range cm (str "\n" spaces) {:line line-n :column (dec end-column)}))
                       (replace-range cm ";;" {:line line-n :column space-n})))
                   (.setCursor cm #js {:line (inc line-n)
                                       :ch   column-n})))

               :uneval-at-point
               (fn [{{:keys [pos]} :magic/cursor
                     zipper        :zipper
                     :as           cm}]
                 (let [bracket-loc (tree/nearest-bracket-region (tree/node-at zipper pos))
                       add-uneval (fn [pos] (replace-range cm "#_" (select-keys pos [:line :column])))
                       remove-uneval (fn [pos] (replace-range cm "" (assoc (select-keys pos [:line :column]) :end-column (+ 2 (:column pos)))))
                       bracket-node (z/node bracket-loc)]
                   (if (.somethingSelected cm)
                     (let [{:keys [line end-line] :as sel-pos} (selection-boundaries cm)]
                       (if (= "#_" (subs (.getSelection cm) 0 2))
                         (remove-uneval sel-pos)
                         (do (add-uneval sel-pos)
                             (select-range cm (cond-> sel-pos
                                                      (= line end-line) (update :end-column #(+ % 2)))))))

                     (if-let [uneval-node (first (filter #(= :uneval (get % :tag))
                                                         (list bracket-node (some-> bracket-loc z/up z/node))))]
                       (remove-uneval uneval-node)
                       (add-uneval bracket-node)))))
               :slurp
               (fn [{{:keys [pos]} :magic/cursor zipper :zipper :as cm}]
                 (let [loc (tree/node-at zipper pos)
                       loc (cond-> loc
                                   (and (or (not (tree/may-contain-children? (z/node loc)))
                                            (not (tree/inside? (z/node loc) pos)))) z/up)
                       {:keys [tag] :as node} (z/node loc)]
                   (when-not (= :base tag)
                     (when-let [next-form (some->> (z/rights loc)
                                                   (filter tree/sexp?)
                                                   first)]
                       (let [[_ rb] (get tree/edges tag)]
                         (replace-range cm rb (tree/boundaries next-form :right))
                         (replace-range cm "" (-> (tree/boundaries node :right)
                                                  (assoc :end-column (dec (:end-column node))))))))))

               :select-pipes
               (fn [cm]
                 (let [sels (.listSelections cm)]
                   (.replaceSelections cm (clj->js (take (count sels) (repeat "|"))))))})

(defn munge-command-key [k]
  (munge (str "magic_"
              (when (keyword? k) (some-> (namespace k) (str "_")))
              (name k))))

(def key-map
  (reduce-kv (fn [m k command-key]
               (let [command-f (get commands command-key #(println (str "Command missing: " command-key)))
                     command-name (munge-command-key command-key)]
                 (aset js/CodeMirror "commands" command-name command-f)
                 (assoc m k command-name)))
             {}
             {"Ctrl-K"        :kill                         ;; cut to end of line / node

              "Cmd-X"         :cut-at-point                 ;; cut/copy selection or
              "Cmd-Backspace" :delete-at-point              ;; nearest node (highlighted).
              "Cmd-C"         :copy-at-point

              "Alt-Left"      :hop-left                     ;; move cursor left/right,
              "Alt-Right"     :hop-right                    ;; touch only node boundaries.

              "Cmd-1"         :expand-selection
              "Cmd-2"         :shrink-selection

              "Cmd-/"         :comment-line
              "Cmd-."         :uneval-at-point

              "Cmd-L"         :select-pipes

              "Shift-Cmd-K"   :slurp}))

'{[:shift :cmd "k"] {:exec :slurp}
  [:ctrl "x"]       {:keys {[:ctrl "x"] {:exec :slurp}}}}
'{#{:shift :cmd "k"} {:exec :slurp}
  #{:ctrl "x"}       {:keys {[:ctrl "x"] {:exec :slurp}}}}