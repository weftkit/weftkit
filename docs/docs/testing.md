---
description: Testing weftkit components - plain unit tests with fakes, package-private components, and running the real graph without a server.
---

# Testing

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
of its type.

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
