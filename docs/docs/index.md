---
title: weftkit
description: weftkit is a lifecycle and dependency injection framework for Bukkit plugins, running on any Bukkit-based Minecraft server from Spigot to Paper. Wiring mistakes fail the build instead of crashing on startup.
---

<p align="center">
  <img src="assets/logo/svg/weftkit-wordmark.svg" alt="weftkit" width="440">
</p>

# Lifecycle management for Bukkit plugins, wired at compile time

weftkit is a lifecycle and dependency injection framework for Bukkit plugins, running on any
Bukkit-based Minecraft server from Spigot to Paper. It brings your components up in dependency
order, runs
their startup and shutdown hooks, registers your Bukkit listeners, and tears everything down in
reverse when the plugin disables. If a component's startup fails, weftkit aborts cleanly and
disables the plugin instead of leaving it half loaded.

The wiring that makes this possible is resolved at compile time. A small annotation processor
validates the whole dependency graph during `javac`, so a missing dependency, a cycle, or a
misused annotation fails the build instead of crashing on startup, and the generated registry
means the hot path does zero reflection.

!!! note "Pre-release"
    weftkit is pre-1.0, so the public API can still change between releases until 1.0.

## Why weftkit

A *weft* is the horizontal thread passed back and forth through the vertical warp threads in
weaving, binding the loose strands into a single fabric. That is what weftkit does. It threads
through your independent components and binds them into one plugin with a single, managed
lifecycle.

If you know Dagger or Guice: weftkit sits closest to Dagger, since wiring is resolved at
compile time with zero runtime reflection. What a general purpose dependency injection
container leaves out is exactly what a plugin needs most, and weftkit makes it the core: load
order, startup and teardown hooks, and Bukkit listener registration are part of the graph, not
an add-on.

## Highlights

<div class="grid cards" markdown>

-   :material-sync:{ .lg .middle } __[Managed lifecycle](lifecycle.md)__

    ---

    Components load in dependency order, run startup and teardown hooks, and shut down in reverse.

-   :material-puzzle:{ .lg .middle } __[Bukkit integration](listeners-and-commands.md)__

    ---

    `onEnable`, `onDisable`, listener registration, and clean shutdown are handled for you.

-   :material-shield-check:{ .lg .middle } __[Compile-time safety](troubleshooting.md)__

    ---

    The graph is validated during `javac`, so wiring mistakes are build errors, not startup crashes.

-   :material-lightning-bolt:{ .lg .middle } __[Zero runtime reflection](components.md)__

    ---

    Components are built by generated factories, so the hot path does no reflection.

-   :material-graph:{ .lg .middle } __[Readable wiring](lifecycle.md#diagnostics)__

    ---

    The processor renders your whole dependency graph to Graphviz, so you can see the plugin's structure at a glance.

-   :material-eye-off:{ .lg .middle } __[Internals stay internal](internal-components.md)__

    ---

    Package-private classes are wired like any other component, so injection never forces your internals to go public.

</div>

[Get started](getting-started.md){ .md-button .md-button--primary }
[Browse the annotations](annotations.md){ .md-button }

## Used by

weftkit was extracted from [SilkSpawners](https://github.com/CorneliusMa/SilkSpawners_v2), which
runs on it as its wiring and lifecycle framework.
