---
title: "Example: player-homes"
description: A homes plugin walked through class by class, a Loader that reads state from disk, aborts startup on corrupt data, provides the loaded value, and saves on shutdown.
---

# player-homes

Per-player homes: `/sethome` remembers where you stand, `/home` teleports you back. Where
[join-leave-messages](join-leave-messages.md) shows the smallest wiring, this example shows
the [lifecycle](../lifecycle.md): a component that loads state from disk before anything uses
it, saves it on shutdown, and aborts startup when the data is unreadable. The full source
lives in
[`examples/bukkit/player-homes`](https://github.com/weftkit/weftkit/tree/main/examples/bukkit/player-homes).

## The plugin main

`HomesPlugin` overrides `onWeftEnable` to attach the command executors with `loader.get(...)`,
the manual step weftkit leaves to the plugin, see
[listeners and commands](../listeners-and-commands.md).

```java
--8<-- "examples/bukkit/player-homes/src/main/java/org/weftkit/examples/homes/HomesPlugin.java"
```

## The loader

`HomesFile` owns `homes.yml`. As a `Loader` it reads the file in `load`, returning false on a
corrupt file so the plugin never runs with silently empty homes, and saves it back in
`unload`. Its `@Provides` getter exposes the parsed `YamlConfiguration` as an injectable
value, captured right after load. The class stays package-private, only the provided value
leaves the package.

```java
--8<-- "examples/bukkit/player-homes/src/main/java/org/weftkit/examples/homes/storage/HomesFile.java"
```

## The store

`HomeStore` injects that provided configuration and turns it into a typed set and find API.
Because it depends on the provided value, it is guaranteed to load after `HomesFile`, no
ordering annotations needed.

```java
--8<-- "examples/bukkit/player-homes/src/main/java/org/weftkit/examples/homes/storage/HomeStore.java"
```

## The commands

[`SetHomeCommand`](https://github.com/weftkit/weftkit/blob/main/examples/bukkit/player-homes/src/main/java/org/weftkit/examples/homes/commands/SetHomeCommand.java)
and
[`HomeCommand`](https://github.com/weftkit/weftkit/blob/main/examples/bukkit/player-homes/src/main/java/org/weftkit/examples/homes/commands/HomeCommand.java)
are plain `CommandExecutor` components injecting the store, declared in `plugin.yml` as usual.

```java
--8<-- "examples/bukkit/player-homes/src/main/java/org/weftkit/examples/homes/commands/SetHomeCommand.java"
```

Homes are held in memory while the server runs and written once on disable. A real plugin
might save on every change instead; the lifecycle hooks stay the same.

## Run it

Build the example as described in the [overview](index.md), drop the jar into a server's
`plugins/` folder, run `/sethome`, wander off, and `/home` brings you back.

To see the aborted startup, put garbage into `plugins/PlayerHomes/homes.yml` and restart: the
plugin logs the parse failure and disables itself instead of coming up empty.
