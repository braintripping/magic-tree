(ns magic-tree.edit-test
  (:require [magic-tree-codemirror.addons]
            [magic-tree-codemirror.edit :refer [munge-command-key]]
            [magic-tree.test-utils :refer [test-exec]]
            [cljs.test :refer-macros [deftest is are]]))

#_(deftest edit-commands
  (are [cmd source post-source]
    (= (test-exec cmd source) post-source)

    :kill "(prn 1 |2 3)" "(prn 1 |)"
    :kill "[1 2 |'a b c' 3 4]" "[1 2 |]"
    :kill "[1 2 '|a b c']" "[1 2 '|']"

    :cut-at-point "|(+ 1)" "|"
    :cut-at-point "(|+ 1)" "(| 1)"

    :hop-left "( )|" "|( )"
    :hop-left "( |)" "(| )"
    :hop-left "( a|)" "( |a)"
    :hop-left "( ab|c)" "( |abc)"
    :hop-left "((|))" "(|())"

    :comment-line "abc|\ndef" ";;abc\ndef|"
    :comment-line "abc|" ";;abc|"

    ))


