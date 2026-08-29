# player-homes

Per-player homes: `/sethome` remembers where you stand, `/home` teleports you back. Shows the
lifecycle end to end: a `Loader` that reads homes from disk at startup, aborts on corrupt
data, `@Provides` the loaded value, and saves on shutdown, plus manual command wiring.

Documented class by class at
[weftkit.org/examples/player-homes](https://weftkit.org/examples/player-homes/).

## Build

From this directory, using the repository's Gradle wrapper:

```sh
../../../gradlew build
```

The jar lands in `build/libs/player-homes-1.0.0.jar`. To use this example as a template for
your own plugin, see the [examples README](../../README.md).
