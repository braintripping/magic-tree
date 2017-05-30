# Magic Tree

ALPHA

> “Leaves turned to soil beneath my feet. Thus it is, trees eat themselves.”
>
> -- _David Mitchell, Cloud Atlas_

Magic Tree is a library for reading and transforming ClojureScript code. Like [rewrite-cljs](https://github.com/rundis/rewrite-cljs), it preserves whitespace and uses zippers as an intermediate data structure. It is small and moldable, built for the purpose of building editors and editing tools.

## What can it do?

1. Parse raw ClojureScript source into an AST (`magic-tree.core/ast`) and corresponding zipper (`magic-tree.core/ast-zip`).
2. Traverse and edit as desired.
3. Emit ClojureScript (`magic-tree.core/string` or `magic-tree.core/sexp`).

## Usage with CodeMirror

The `magic-tree-codemirror` namespaces include work on bracket highlighting and paredit functionality. Implementing editor behaviour is hard to do in a general way and so this code is rather tightly coupled to CodeMirror.

An example of real-world usage can be found in [Maria](https://github.com/mhuebert/maria), a beginner-friendly ClojureScript REPL.

## Testing

`lein doo phantom test`