---
title: "Examples: complete runnable Bukkit plugins"
description: Complete runnable example plugins built on weftkit, from the smallest wired listener to a module with feature flags, each documented class by class.
---

# Examples

The [examples](https://github.com/weftkit/weftkit/tree/main/examples) are complete runnable
plugins, grouped by platform adapter. Each page here walks through one of them class by class,
with the source embedded from the repository, so the code you read is the code that compiles.

| Example | Shows |
| --- | --- |
| [join-leave-messages](join-leave-messages.md) | The smallest useful plugin: a wired listener that replaces the join and leave broadcasts, with the message formatting injected as a component. |
| [player-homes](player-homes.md) | `/sethome` and `/home` backed by a `Loader` that reads homes from disk at startup, saves on shutdown, and `@Provides` the loaded data. Shows manual command wiring. |
| [announcer](announcer.md) | Rotating broadcasts, each line its own component: a `@WeftModule` enumerates them with `Weft.all`, one is gated by a `@FeatureFlag` and another depends on it as `Optional<T>`, and `@ModuleSettings` sets the pace. |

## Running an example

Each example is a standalone Gradle build inside the repository. Clone the repository, then
build one from its directory with the repository's Gradle wrapper:

```sh
cd examples/bukkit/join-leave-messages
../../../gradlew build
```

The jar lands in the example's `build/libs/` folder. Drop it into a server's `plugins/` folder
to see it run. `./gradlew buildExamples` from the repository root builds all of them.

!!! note "WeftWiring is generated"
    `WeftWiring` is produced by the annotation processor during compilation, so a fresh
    checkout shows it unresolved in the IDE until the first build. Enable annotation processing
    in your IDE to regenerate it as you edit.

## Using an example as a template

Inside the repository the examples compose the weftkit sources via `includeBuild`, so they
always compile against the local code. To start your own plugin from one, remove the
`includeBuild("../../..")` line from its `settings.gradle.kts` and set the `org.weftkit`
dependencies in `build.gradle.kts` to the latest release, as shown in
[getting started](../getting-started.md#installation).
