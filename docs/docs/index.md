---
title: weftkit
description: weftkit manages the lifecycle of Bukkit plugins with compile-time wiring. Components load in dependency order, listeners register themselves, and wiring mistakes fail the build instead of crashing the server.
---

<p align="center">
  <img src="assets/logo/png/weftkit-wordmark.png" alt="weftkit" width="440">
</p>

# Lifecycle management for Bukkit plugins, wired at compile time

!!! note "Pre-release"
    weftkit is pre-1.0, so the public API can still change between releases until 1.0.

weftkit runs your plugin's lifecycle. It brings your components up in dependency order, runs
their startup and shutdown hooks, registers your Bukkit listeners, and tears everything down in
reverse when the plugin disables. If a component's startup fails, weftkit aborts cleanly and
disables the plugin instead of leaving it half loaded.

The wiring that makes this possible is resolved at compile time. A small annotation processor
validates the whole dependency graph during `javac`, so a missing dependency, a cycle, or a
misused annotation fails the build instead of crashing on startup, and the generated registry
means the hot path does zero reflection.

## Why weftkit

A *weft* is the horizontal thread passed back and forth through the vertical warp threads in
weaving, binding the loose strands into a single fabric. That is what weftkit does. It threads
through your independent components and binds them into one plugin with a single, managed
lifecycle.

## Highlights

<div class="grid cards" markdown>

-   :material-sync:{ .lg .middle } __Managed lifecycle__

    ---

    Components load in dependency order, run startup and teardown hooks, and shut down in reverse.

-   :material-puzzle:{ .lg .middle } __Bukkit integration__

    ---

    `onEnable`, `onDisable`, listener registration, and clean shutdown are handled for you.

-   :material-shield-check:{ .lg .middle } __Compile-time safety__

    ---

    The graph is validated during `javac`, so wiring mistakes are build errors, not startup crashes.

-   :material-lightning-bolt:{ .lg .middle } __Zero runtime reflection__

    ---

    Components are built by generated factories, so the hot path does no reflection.

-   :material-graph:{ .lg .middle } __Readable wiring__

    ---

    The processor renders your whole dependency graph to Graphviz, so you can see the plugin's structure at a glance.

-   :material-eye-off:{ .lg .middle } __Internals stay internal__

    ---

    Package-private classes are wired like any other component, so injection never forces your internals to go public.

</div>

[Get started](getting-started.md){ .md-button .md-button--primary }
[Browse the annotations](annotations.md){ .md-button }

## Used by

weftkit was extracted from [SilkSpawners](https://github.com/CorneliusMa/SilkSpawners_v2), which
runs on it as its wiring and lifecycle framework.
