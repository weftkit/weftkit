---
title: "Testing wired components without a server"
description: "How to test weftkit components: plain unit tests with fakes, package-private components in same-package tests, and the real graph running without a server."
---

# Testing

weftkit is built to stay out of the way under test. Wiring is resolved at compile time and
injection happens through plain constructors, so components need no framework to be
constructed, and the whole graph loads and unloads inside a unit test without a Bukkit server.
This page covers the three levels: testing a component alone, testing package-private
internals, and running the real graph.

## Components are plain classes

Constructor injection keeps components testable without weftkit. Instantiate them directly and
pass fakes:

```java
SpawnerService service = new SpawnerService(new FakeStorage());
```

No loader and no annotations involved: `@Wired` changes how production wiring builds the class,
not what the class is.

## Package-private components

Tests in the same package construct package-private components like any other class, so hiding a
component costs no testability. See [internal components](internal-components.md).

## Running the real graph

`WeftLoader` runs anywhere, a Bukkit server is not required. Pass fakes as ambient values: an
ambient value satisfies a dependency before the graph does, so it reaches every injection point
of its declared type. Shadowing a wired implementation needs its abstraction
[declared](components.md#ambient-roots), e.g. `@Registry(ambient = SpawnerStorage.class)`. The
wired implementation backs the type when no value arrives, so production stays unchanged.

```java
FakeStorage storage = new FakeStorage();
WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, storage);
assertTrue(loader.load());
assertSame(storage, loader.get(SpawnerService.class).storage());
loader.unload();
```

Two boundaries to know:

- Eager singletons still load when an ambient fake shadows their injections, so their `load`
  hooks run. Fake the dependencies those hooks use, or keep hooks free of outside effects.
- Components that inject the plugin cannot run headless, since a `JavaPlugin` only exists on a
  server (or under a server mock like MockBukkit). Keeping direct plugin dependencies rare keeps
  most of the graph testable without one.
- `load()` does not activate [modules](modules.md). A test that needs their hooks drives them
  with `Modules.activate(loader)` and `Modules.deactivate(loader)` from
  `org.weftkit.wiring.runtime.spi`.

An ambient `FeatureFlags` pins [feature flags](modules.md#feature-flags) for a test, since an
ambient value wins over a wired implementation. `FeatureFlags` is always declared implicitly.

## Related

- [Components](components.md): constructor injection, ambient roots, and optional dependencies
- [Internal components](internal-components.md): why hiding a component costs no testability
- [Lifecycle](lifecycle.md): exactly what `load()` and `unload()` run
- [Modules and feature flags](modules.md): what `Modules.activate` drives in a test
