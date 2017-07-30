(ns magic-tree.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            #_[magic-tree.edit-test]
            [magic-tree.parse-test]))

(doo-tests #_'magic-tree.edit-test
           'magic-tree.parse-test)
