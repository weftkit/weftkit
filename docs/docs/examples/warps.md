---
title: "Example: warps"
description: A Paper warps plugin walked through class by class, Brigadier commands registered through @PaperCommand with no plugin.yml entry.
---

# warps

Named warps: `/warp set <name>` saves where you stand, `/warp <name>` teleports you there, and
`/warps` lists them. The example shows [Paper commands](../listeners-and-commands.md#paper-commands):
one Brigadier tree and one `BasicCommand`, both registered by the bundled `paper:commands`
module. The full source lives in
[`examples/paper/warps`](https://github.com/weftkit/weftkit/tree/main/examples/paper/warps).

## The plugin main

`WarpsPlugin` is just the registry hook. Its `paper-plugin.yml` declares no commands.

```java
--8<-- "examples/paper/warps/src/main/java/org/weftkit/examples/warps/WarpsPlugin.java"
```

```yaml
--8<-- "examples/paper/warps/src/main/resources/paper-plugin.yml"
```

## The store

`WarpStore` holds the warps in memory, sorted by name.

```java
--8<-- "examples/paper/warps/src/main/java/org/weftkit/examples/warps/storage/WarpStore.java"
```

## The tree command

`WarpCommand` is a `CommandTree`: a `set` subcommand behind a permission, and a name argument
that suggests the existing warps. Brigadier prefers a literal over an argument at the same level,
so `/warp set` could never teleport to a warp named `set`, and the subcommand refuses that name. The root requires a player, so the console never sees it.

```java
--8<-- "examples/paper/warps/src/main/java/org/weftkit/examples/warps/commands/WarpCommand.java"
```

## The basic command

`WarpsCommand` is a plain `BasicCommand` with an alias.

```java
--8<-- "examples/paper/warps/src/main/java/org/weftkit/examples/warps/commands/WarpsCommand.java"
```

## Run it

Build the example as described in the [overview](index.md), drop the jar into the `plugins/`
folder of a Paper 1.21.1 or newer server, run `/warp set spawn`, wander off, and `/warp spawn`
brings you back.
