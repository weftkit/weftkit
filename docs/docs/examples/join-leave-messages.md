---
title: "Example: join-leave-messages"
description: The smallest useful weftkit plugin, walked through class by class, a wired listener with an injected message component and no manual registration.
---

# join-leave-messages

The smallest useful weftkit plugin: it replaces the default join and leave broadcasts with its
own messages. Three classes show the whole wiring model. The full source lives in
[`examples/bukkit/join-leave-messages`](https://github.com/weftkit/weftkit/tree/main/examples/bukkit/join-leave-messages).

The walkthrough follows the order the server sees: the plugin main boots the graph, the
component holds the message logic, and the listener connects both to Bukkit's events. The
build setup itself is covered in [getting started](../getting-started.md); this page focuses
on the wiring.

## The plugin main

`JoinLeavePlugin` carries `@Registry`, extends `WeftPlugin`, and points at the generated
`WeftWiring`. Nothing else.

```java
--8<-- "examples/bukkit/join-leave-messages/src/main/java/org/weftkit/examples/joinleave/JoinLeavePlugin.java"
```

## The component

`Messages` is a plain `@Wired @Singleton` component that formats the broadcasts. It is public
because it is injected across packages.

```java
--8<-- "examples/bukkit/join-leave-messages/src/main/java/org/weftkit/examples/joinleave/messages/Messages.java"
```

## The listener

`ConnectionListener` gets `Messages` injected through its constructor and handles
`PlayerJoinEvent` and `PlayerQuitEvent`. weftkit registers it automatically, so there is no
`registerEvents` call anywhere. It stays package-private even in its own package: weftkit
generates a wiring holder per package with non-public components, see
[internal components](../internal-components.md).

```java
--8<-- "examples/bukkit/join-leave-messages/src/main/java/org/weftkit/examples/joinleave/listeners/ConnectionListener.java"
```

## Layout and build

The layout is the usual feature-package shape: the plugin main and the generated `WeftWiring`
at the root, everything else grouped by concern.

The build follows the [distribution guide](../distribution.md): Shadow bundles weftkit into
the plugin jar and relocates it, so several weftkit plugins can coexist on one server.

## Run it

Build the example as described in the [overview](index.md), drop the jar into a server's
`plugins/` folder, join, and the custom message appears.

The shape is the takeaway: one listener, one injected collaborator, and a plugin main that
only anchors the registry. Larger plugins repeat exactly this pattern;
[player-homes](player-homes.md) adds lifecycle hooks and two commands on top of it.
