# Magic Tree

> “Leaves turned to soil beneath my feet. Thus it is, trees eat themselves.”
>
> -- _David Mitchell, Cloud Atlas_

Magic Tree is a library for reading and transforming ClojureScript, similar to [rewrite-cljs](https://github.com/rundis/rewrite-cljs). It is small and moldable, and built for the purpose of experimenting with browser-based editing experiences. It is whitespace-aware, so you can parse source code, modify it programatically, and then re-emit strings without unintentional changes to formatting.

ALPHA

## What can it do?

1. Parse raw ClojureScript source into an AST (`magic-tree.core/ast`).
2. Turn that AST into a zipper (`magic-tree.core/ast-zip`).
3. Traverse and edit the AST as desired.
4. Emit ClojureScript strings or forms from AST nodes (`magic-tree.core/string` or `magic-tree.core/sexp`).

## Usage with CodeMirror

To see Magic Tree in action, the `magic-tree.codemirror` namespaces contain implementations of bracket highlighting and paredit (partial coverage).

Real-world usage can be found in the source for [Maria](https://github.com/mhuebert/maria), a beginner-friendly REPL currently under development.

## Testing

`lein doo phantom test`

## Why a new library?

We've had grand mystical acid-trip visions of future programming-ish experiences that can't be easily built with existing tools.