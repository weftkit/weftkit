# player-homes

Per-player homes: `/sethome` remembers where you stand, `/home` teleports you back. Where
[`join-leave-messages`](../join-leave-messages) shows the smallest wiring, this example shows
the [lifecycle](https://weftkit.org/lifecycle/): a component that loads state from disk before
anything uses it, saves it on shutdown, and aborts startup when the data is unreadable.

- [`HomesPlugin`](src/main/java/org/weftkit/examples/homes/HomesPlugin.java) is the plugin main.
  Its `onWeftEnable` attaches the command executors with `loader.get(...)`, the manual step
  weftkit leaves to the plugin, see
  [listeners and commands](https://weftkit.org/listeners-and-commands/).
- [`storage/HomesFile`](src/main/java/org/weftkit/examples/homes/storage/HomesFile.java) owns
  `homes.yml`. As a `Loader` it reads the file in `load`, returning false on a corrupt file so
  the plugin never runs with silently empty homes, and saves it back in `unload`. Its
  `@Provides` getter exposes the parsed `YamlConfiguration` as an injectable value, captured
  right after load. The class stays package-private, only the provided value leaves the package.
- [`storage/HomeStore`](src/main/java/org/weftkit/examples/homes/storage/HomeStore.java) injects
  that provided configuration and turns it into a typed set and find API. Because it depends on
  the provided value, it is guaranteed to load after `HomesFile`, no ordering annotations needed.
- [`commands/SetHomeCommand`](src/main/java/org/weftkit/examples/homes/commands/SetHomeCommand.java)
  and [`commands/HomeCommand`](src/main/java/org/weftkit/examples/homes/commands/HomeCommand.java)
  are plain `CommandExecutor` components injecting the store. Both are declared in
  [`plugin.yml`](src/main/resources/plugin.yml) as usual.

Homes are held in memory while the server runs and written once on disable. A real plugin might
save on every change instead; the lifecycle hooks stay the same.

## Build

From this directory, using the repository's Gradle wrapper:

```sh
../../../gradlew build
```

The jar lands in `build/libs/player-homes-1.0.0.jar`. Drop it into a server's `plugins/` folder,
run `/sethome`, wander off, and `/home` brings you back. To see the aborted startup, put garbage
into `plugins/PlayerHomes/homes.yml` and restart: the plugin logs the parse failure and disables
itself instead of coming up empty.

Note that `WeftWiring` is generated during compilation, so a fresh checkout shows it unresolved
in the IDE until the first build. Enable annotation processing in your IDE to regenerate it as
you edit.

## Using this as a template

The example compiles against the weftkit sources in this repository through the
`includeBuild("../../..")` line in [`settings.gradle.kts`](settings.gradle.kts). To start your own
plugin from it, remove that line and set the `org.weftkit` dependencies in
[`build.gradle.kts`](build.gradle.kts) to the latest release.
