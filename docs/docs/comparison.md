---
title: weftkit vs Dagger, Guice, and manual wiring
description: How weftkit compares to wiring a Bukkit plugin by hand, to Guice, and to Dagger. An honest feature table and guidance on when each is the right choice.
---

# weftkit vs Dagger, Guice, and manual wiring

For most Bukkit plugins the alternative is not another framework. It is constructors called by
hand in `onEnable`, teardown written in `onDisable`, and load order maintained by convention.
The general purpose containers, [Guice](https://github.com/google/guice) and
[Dagger](https://dagger.dev), replace the constructor calls but leave the lifecycle to you. This page lays out where each approach stands, including the rows where
weftkit is not the right answer.

## At a glance

|  | Manual wiring | weftkit | Dagger | Guice |
| --- | --- | --- | --- | --- |
| Dependency resolution | By hand in `onEnable` | Compile time, generated registry | Compile time, generated code | Runtime, via reflection |
| Wiring mistakes surface | On the server, often as an NPE | As a `javac` error | As a `javac` error | When the injector starts, on the server |
| Managed lifecycle: load order, startup and teardown hooks, reverse shutdown | You write and maintain it | Built in, the core of the framework | Not included | Not included |
| Bukkit listener registration | `registerEvents` per listener | Automatic for `@Wired` listeners | By hand | By hand |
| Runtime reflection | None | None | None | Throughout |
| Weight added to the plugin jar | None | Small runtime, bStats is the only third party dependency | Small runtime | Guice plus its dependencies, on the order of megabytes shaded |
| Use outside Bukkit | Not applicable | No, weftkit is Bukkit only | Yes, general purpose | Yes, general purpose; Sponge and Velocity are built on it |
| Maturity and ecosystem | Not applicable | Pre-1.0, runs one production plugin | Very mature, large ecosystem | Very mature, large ecosystem |

## When manual wiring is enough

A small plugin with a handful of classes and no teardown does not need a framework. Manual
wiring stops being enough when components depend on each other and load order starts to matter,
when `onDisable` must undo what `onEnable` did, or when a forgotten `registerEvents` call
silently drops a listener. Those are exactly the failure modes weftkit moves into the compiler
and the managed [lifecycle](lifecycle.md).

## Coming from Guice

Guice resolves the graph at runtime with reflection, so a missing binding surfaces when the
injector starts, on the server. weftkit resolves the same graph during `javac`, so the
equivalent mistake [fails the build](troubleshooting.md) instead. Guice is the right choice
when you need its flexibility, its ecosystem, or a platform that already provides it, as Sponge
and Velocity do. If your target is a Bukkit server and your injector exists to start and stop
components in order, weftkit does that part natively and weighs far less in the shaded jar.

## Coming from Dagger

Dagger and weftkit share the core idea: the dependency graph is validated at compile time, the
wiring is generated code, and nothing reflects at runtime. The difference is scope. Dagger
stops at construction; ordering startup, running teardown hooks, and registering Bukkit
listeners remain your code. weftkit treats those as the point: components declare
[lifecycle hooks](lifecycle.md), load in dependency order, shut down in reverse, and
[listeners register themselves](listeners-and-commands.md). Pick Dagger when the graph must
span beyond Bukkit or you want its ecosystem. Pick weftkit when the graph exists to run a
plugin.

## What weftkit does not do

weftkit is not a general purpose container. It targets Bukkit only, and it offers neither
Guice's runtime flexibility nor Dagger's ecosystem. The API is pre-1.0 and can still change
between releases. If any of those matter more than a managed lifecycle and build-time wiring
errors, one of the alternatives on this page is the better tool.

If weftkit fits, [getting started](getting-started.md) walks through a first plugin. weftkit
is a young project, and every adopter and tester helps:
[feedback and issues](https://github.com/weftkit/weftkit/issues) are always welcome.
