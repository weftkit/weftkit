---
description: Reference for every weftkit annotation and the Loader lifecycle interface.
---

# Annotations

weftkit's vocabulary is a handful of annotations plus the `Loader` interface. The processor
validates how you use them during compilation.

## @Registry

Marks the single class that anchors the wiring, usually your plugin main extending
`WeftPlugin`. The component registry
is generated into its package, and the class itself is injectable as an ambient dependency along
with any of its constructor parameters.

## @Wired

Marks a class as created through the loader. Its constructor is the injection point, and the
processor validates every parameter against the graph. A `@Wired` class must be a concrete,
top-level or static nested class that is public or package-private, with exactly one accessible
constructor. A package-private component stays invisible outside its package: the processor
generates a small `WeftWiring` holder next to it that hands its wiring to the registry, so
internals never need to go public just to be injectable.

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

Some plugins keep global state in static fields: a legacy config class or a static service
accessor that other code reads directly instead of being injected. Static state is invisible to
the dependency graph. Nothing tells weftkit that one component fills those fields during load
and others read them, so nothing would order the load accordingly.

`@StaticHolder` makes that dependency explicit. Mark the class holding the static state, let one
`@Wired` singleton declare `@Initializes(TheHolder.class)` and fill the holder in its `load`
hook, and let every component that reads the holder during its own construction or load declare
`@Requires(TheHolder.class)`. Each requirement becomes a load order edge, so readers load after
the initializer. As a safety net the processor scans every component's constructor, field
initializers, and `load` method, and a holder access in that window without a matching
`@Requires` fails the build.

The scan sees direct accesses in your own javac-compiled sources. Reads through helper methods,
reflection, or foreign jars stay invisible to it, which is why `@Requires` is declared explicitly
instead of being inferred from the scan.

### Migration only

The holder annotations are a bridge for plugins adopting weftkit: annotating the existing
static state brings it into the managed load order without touching the legacy code that
reads it. `@StaticHolder` is deprecated without a removal plan to mark the bridge as
temporary. New components use constructor injection from the start, and each holder carries
the deprecation warning until its readers are migrated. Suppress it per holder with
`@SuppressWarnings("deprecation")` for as long as the holder is needed.

Finishing the migration means rewriting the readers that adoption deferred. Move the static
fields into the singleton that fills them, with the `load` hook and its parsing logic staying
as they are, and let each reader inject the singleton and call a getter instead of reading
static state. `@Initializes` and `@Requires` disappear, since the constructor dependency
already orders the load. Reading through the owner also keeps every reader current if the
plugin later adds an in-place [reload](lifecycle.md#reloading):

```java
// The initializer after the migration: the former holder's fields are its own now
@Wired
@Singleton
public final class Config implements Loader {

    private Settings settings;

    @Override
    public boolean load() {
        settings = parse();
        return true;
    }

    public Settings settings() {
        return settings;
    }
}

// A former @Requires reader, ordered by its constructor dependency instead
@Wired
@Singleton
final class Spawner {

    private final Config config;

    Spawner(Config config) {
        this.config = config;
    }
}
```

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
