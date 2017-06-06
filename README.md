# Magic Tree

> “Leaves turned to soil beneath my feet. Thus it is, trees eat themselves.”
>
> -- _David Mitchell, Cloud Atlas_

Magic Tree is a library for reading and transforming ClojureScript code. Like [rewrite-cljs](https://github.com/rundis/rewrite-cljs), it preserves whitespace and uses zippers as an intermediate data structure. It is small and moldable, built for the purpose of building editors and editing tools. 

The current form of Magic Tree is just a first step to a bigger vision.

## What can it do?

1. Parse raw ClojureScript source into an AST (`magic-tree.core/ast`) and corresponding zipper (`magic-tree.core/ast-zip`).
2. Traverse and edit as desired.
3. Emit ClojureScript (`magic-tree.core/string` or `magic-tree.core/sexp`).

## Usage with CodeMirror

The `magic-tree-codemirror` namespaces include work on bracket highlighting and paredit functionality. Implementing editor behaviour is hard to do in a general way and so this code is tightly coupled to CodeMirror. This code should be considered temporary 'scratch' code that **will not** be maintained indefinitely.

An example of real-world usage can be found in [Maria](https://github.com/mhuebert/maria), a beginner-friendly ClojureScript REPL.

## Testing

`lein doo phantom test`

## Future

In addition to being 'whitespace-aware', a magic-tree AST should also be 'cursor-aware' and 'selection-aware', and thus encapsulate nearly the full state of an editor. This will simplify the implementation of new editing commands, make testing easier, and allow for more freedoms in the visual representation and manipulation of code. (CodeMirror will be just one of many possible 'views' on an AST, one which happens to use a string representation.)
