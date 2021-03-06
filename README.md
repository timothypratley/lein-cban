# lein-cban

Clojure by anyother name.

>What's in a name? that which we call a rose<br>
By any other name would smell as sweet;<br>
_Romeo and Juliet (William Shakespeare)_

`lein-cban` is a Leiningen plugin to translate your Clojure code into other written languages.
It generates a new namespace that alias functions/macros/vars from your original namespace.

See also [cban](https://github.com/timothypratley/cban)
which provides translations for clojure.core, and demonstrates how to use the plugin.


## Usage

Put `[lein-cban "0.1.0"]` into the `:plugins` vector of your project.clj

Put translation csv files in a `translations` directory.
You should create a sub-directory per language,
and inside create a translation file per namespace.
The translation file is a csv file with columns:
"existing,alias,docstring,comment"

`existing` and `alias` are required, `docstring` and `comment` are optional.

To create the translation namespaces, execute:

    $ lein cban

This will produce cljc files in `target/cban`,
and write an edn file containing the full translation map to `resources/your-project-name-translation-map.edn`

You can override the defaults parameters in your project like so:
```clojure
:cban {:input-dir "input-dir"
       :output-dir "output-dir"
       :output-map-to "translation-map.edn"}
```

`lein-cban` works best on cljc files because they can be loaded in Clojure
so that translations can be checked for both Clojure and ClojureScript. Consider wrapping any ClojureScript specific code in
[reader conditionals](https://github.com/clojure/clojurescript/wiki/Using-cljc).
If `lein-cban` is unable to resolve a symbol, it will create the alias anyway,
and report a warning.

## License

Copyright © 2017 Elango Cheran and Timothy Pratley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
