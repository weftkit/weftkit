# announcer

Rotating server broadcasts, each line its own wired component. Shows modules end to end: a
`@WeftModule` discovers the lines with `Weft.all`, `@ModuleSettings` sets the pace, one line is
gated by a `@FeatureFlag` resolved from `config.yml`, and another depends on that flagged line as
`Optional<T>`.

Documented class by class at
[weftkit.org/examples/announcer](https://weftkit.org/examples/announcer/).

## Build

From this directory, using the repository's Gradle wrapper:

```sh
../../../gradlew build
```

The jar lands in `build/libs/announcer-1.0.0.jar`. To use this example as a template for your
own plugin, see the [examples README](../../README.md).
