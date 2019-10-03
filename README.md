Atlas
=====

Atlas is a *plain-and-simple* binary transformer for Java artifacts, providing a simple API
to manipulate Jars as you see fit.

```java
final Atlas atlas = new Atlas();
atlas.install((ctx) -> new JarEntryRemappingTransformer(
        new LorenzRemapper(mappings)
));
atlas.run(Paths.get("input.jar"), Paths.get("output.jar"));
```

## License

Atlas is made available under the **Mozilla Public License 2.0**, you can find a copy within
Atlas' binary, or within [this repository](LICENSE.txt).
