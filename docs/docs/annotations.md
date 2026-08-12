---
description: Reference for every weftkit annotation and the Loader lifecycle interface.
---

# Annotations

weftkit's vocabulary is a handful of annotations plus the `Loader` interface. The processor
validates how you use them during compilation.

## @Registry

Marks the single class that anchors the wiring, usually your plugin main. The component registry
is generated into its package, and the class itself is injectable as an ambient dependency along
with any of its constructor parameters.

## @Wired

Marks a class as created through the loader. Its constructor is the injection point, and the
processor validates every parameter against the graph. A `@Wired` class must be a concrete,
public, top-level or static nested class with exactly one public constructor.

## @Singleton

Marks a `@Wired` component as created once and injected by type from then on. A plain `@Wired`
component is created fresh for every injection instead. With `lazy = true` the singleton is
created on its first injection instead of during load, and it cannot implement `Loader` or carry
`@Provides`, `@Initializes`, or `@Requires`.

## @Provides

Marks a public no-argument getter on a `@Wired` singleton. Its return value becomes an injectable
dependency once the owner has loaded. The value is captured right after `load`, and a getter that
still returns null at that point fails startup.

## @Qualified

Distinguishes multiple dependencies of the same type. On a `@Wired` class or a `@Provides`
getter it tags what is offered, and on a constructor parameter it selects the implementation or
product carrying that tag.

## @StaticHolder

Marks a class whose static state is set up by an initializing loader. `@Initializes` and
`@Requires` only accept classes carrying this annotation, and the processor scans every
component's constructor, field initializers, and `load` method, so accessing a holder in that
window without declaring `@Requires` fails the build. The scan covers direct accesses in your own
sources compiled with javac. Helper methods, reflection, and foreign jars stay invisible to it.

## @Initializes

Declares the `@StaticHolder` classes a `@Wired` singleton initializes during load. Components
that require those holders load after it.

## @Requires

Declares the `@StaticHolder` classes a component reads, ordering it after the singleton that
initializes them. The declaration stays explicit rather than being inferred from the access scan:
reads through helper methods are invisible to the scan, and the load order must not depend on
which compiler ran. `@Requires` drives the graph, the scan only catches forgotten declarations.

## Loader

Implement `Loader` to hook the lifecycle. `load` runs when the singleton is created, and
returning `false` aborts startup. `unload` runs on shutdown in reverse load order. Only
singletons may implement `Loader`, since a per-injection component would never have its `load`
called.
