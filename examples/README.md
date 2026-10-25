# Examples

Runnable example plugins built on weftkit, grouped by platform adapter. Each is documented
class by class on the docs site, see [weftkit.org/examples](https://weftkit.org/examples/).

| Example | Description |
| --- | --- |
| [`bukkit/join-leave-messages`](bukkit/join-leave-messages) | The smallest useful plugin: a wired listener that replaces the join and leave broadcasts, with the message formatting injected as a component. |
| [`bukkit/player-homes`](bukkit/player-homes) | `/sethome` and `/home` backed by a `Loader` that reads homes from disk at startup, saves on shutdown, and `@Provides` the loaded data. Shows manual command wiring. |
| [`bukkit/announcer`](bukkit/announcer) | Rotating broadcasts, each line its own component: a `@WeftModule` enumerates them with `Weft.all`, one is gated by a `@FeatureFlag` and another depends on it as `Optional<T>`, and `@ModuleSettings` sets the pace. |
| [`paper/warps`](paper/warps) | `/warp` as a Brigadier `CommandTree` with subcommands and suggestions, `/warps` as a `BasicCommand`, both registered through `@PaperCommand` with no `plugin.yml` entry. |

Each example is a standalone Gradle build that composes the weftkit sources in this repository
via `includeBuild`, so it always compiles against the local code. To use one as a template for
your own plugin, remove the `includeBuild("../../..")` line from its `settings.gradle.kts` and
set the weftkit dependencies to the latest release.

Build every example from the repository root:

```sh
./gradlew buildExamples
```

Or a single one from its own directory:

```sh
cd bukkit/join-leave-messages
../../../gradlew build
```
