---
title: "Example: announcer"
description: A module example walked through class by class, component enumeration with Weft.all, module settings with generated defaults, and a feature flagged component.
---

# announcer

Rotating server broadcasts. Each broadcast line is its own wired component, and a
[module](../modules.md) discovers them all, runs the timer, and tears it down again. This is
the example for `@WeftModule`, `Weft.all`, `@ModuleSettings`, and `@FeatureFlag`, including an
optional dependency on a flagged component. The full source lives in
[`examples/bukkit/announcer`](https://github.com/weftkit/weftkit/tree/main/examples/bukkit/announcer).

The packages follow the module split: `broadcast` is the engine, written as a library would
ship it, `announcements` is this plugin's content, and neither knows the other's classes
beyond the `Announcement` interface.

## The plugin main

`AnnouncerPlugin` joins the module through `@Registry(modules = AnnouncerModule.class)`.
Bundled adapter modules need no such declaration, plugin-local and third-party ones do.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/AnnouncerPlugin.java"
```

## The extension point

`Announcement` is one method, one broadcast line.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/broadcast/Announcement.java"
```

## The module

`AnnouncerModule` implements `ModuleLifecycle`. In `activate` it enumerates every
`Announcement` with `weft.all`, the one capability constructor injection cannot express, and
starts a repeating task; `deactivate` cancels it. Adding a broadcast line is adding a class,
nothing else changes.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/broadcast/AnnouncerModule.java"
```

`AnnouncerModules` holds the module id, so `@Registry(disable = AnnouncerModules.BROADCASTS)`
can switch the module off without importing it.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/broadcast/AnnouncerModules.java"
```

## The settings

`AnnouncerSettings` is the module's `@ModuleSettings` interface. No implementation is wired,
so the processor generates one that keeps the one minute default. Wire a component
implementing it to change the pace.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/broadcast/AnnouncerSettings.java"
```

## The content

[`announcements/`](https://github.com/weftkit/weftkit/tree/main/examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/announcements)
holds the broadcast lines, each a package-private component the module only ever sees through
enumeration. `SeasonalAnnouncement` is gated by `@FeatureFlag`: while the flag is off the
component is never constructed and `weft.all` skips it.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/announcements/SeasonalAnnouncement.java"
```

## Depending on a flagged component

`HeadlineAnnouncement` reaches `SeasonalAnnouncement` directly, not through enumeration. Nothing
may *require* a flagged component, since it may never be constructed, so the dependency is an
`Optional<SeasonalAnnouncement>`: it holds the component while the flag is on and is empty while
it is off. This is how a config-gated feature reaches a consumer that should keep working without
it, with the wiring stating the optionality instead of the consumer reading the flag itself.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/announcements/HeadlineAnnouncement.java"
```

## The flags

`ConfigFlags` resolves flags from `config.yml`, which it writes out on first load. Without
such a `FeatureFlags` component, each flag's `enabledByDefault` decides.

```java
--8<-- "examples/bukkit/announcer/src/main/java/org/weftkit/examples/announcer/flags/ConfigFlags.java"
```

## Run it

Build the example as described in the [overview](index.md), drop the jar into a server's
`plugins/` folder, and a broadcast appears every minute, cycling through the wired
announcements.

Flip `flags.seasonal-broadcast` in `plugins/Announcer/config.yml` and restart to watch the
flagged component appear and disappear from the rotation, and the headline switch between reusing
the seasonal text and its fallback as its `Optional` dependency fills and empties.
