(ns magic-tree.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [magic-tree.edit-test]
            [magic-tree.parse-test]))

(doo-tests 'magic-tree.edit-test
           'magic-tree.parse-test)
