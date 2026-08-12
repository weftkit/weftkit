---
description: Constructor injection in weftkit - singletons, interface bindings, @Provides products, qualifiers, optional dependencies, and lazy singletons.
---

# Components

A component is a plain class that weftkit constructs for you. `@Wired` marks a class as part of
the graph, and its constructor is the injection point.

## Singletons and plain components

`@Singleton` creates a component once during load and injects that instance by type from then on.
A plain `@Wired` component without `@Singleton` is created fresh for every injection. Use a
singleton for anything that holds state or does startup work, and a plain component for
throwaway, per-use objects.

```java
@Wired
@Singleton
public final class SpawnerService {

    private final Config config;

    public SpawnerService(Config config) {
        this.config = config;
    }
}
```

## Constructor injection

Every constructor parameter is resolved from the graph. A parameter can be another component, a
value exposed with `@Provides`, or an ambient root. The plugin main is an ambient root because it
carries `@Registry`, so any component can take it.

```java
public SpawnerService(HelloPlugin plugin, Config config) { ... }
```

## Binding to interfaces

A constructor can depend on an interface or an abstract class. When exactly one `@Wired`
component implements it, the processor binds the two at compile time and that implementation is
injected. For abstractions declared in your own sources, zero or several implementations fail
the build, so the binding is never ambiguous at runtime. An abstraction from another module or
jar binds best effort instead: exactly one implementation binds, and zero or several stay
unbound without an error, since such a value may legitimately arrive as an explicit argument or
an ambient root.

```java
public interface SpawnerStorage {
    void save(Spawner spawner);
}

@Wired
@Singleton
public final class SqlSpawnerStorage implements SpawnerStorage { ... }

@Wired
@Singleton
public final class SpawnerService {

    private final SpawnerStorage storage;

    public SpawnerService(SpawnerStorage storage) {
        this.storage = storage;
    }
}
```

Swapping the SQL implementation for a file based one is a one class change, and tests can
construct `SpawnerService` with a fake directly since components are plain classes. `get` also
resolves through bindings, so `loader.get(SpawnerStorage.class)` returns the bound singleton.

## Products with @Provides

A public no-argument getter on a singleton, annotated `@Provides`, exposes its return value to
the graph once the owner has loaded. This is how you inject values you build at runtime rather
than wire by type.

```java
@Wired
@Singleton
public final class Config implements Loader {

    private Greeting greeting;

    @Override
    public boolean load() {
        greeting = new Greeting("Hello");
        return true;
    }

    @Provides
    public Greeting greeting() {
        return greeting;
    }
}
```

Any component can now take a `Greeting` in its constructor.

weftkit captures the value once, right after the owner finishes loading. A getter that still
returns null at that point fails startup, so a `load` that forgot to set its field is caught at
enable time instead of at the first injection, and after shutdown the captured values are
dropped again.

## Reaching your components

`BukkitWeft.enable` returns a `WeftLoader`. Use it to reach singletons by type.

```java
Config config = loader.get(Config.class);
```

For plain components, `create` builds a fresh instance, and `createAll` collects every component
assignable to a type. Extra arguments are matched to constructor parameters by type.

```java
Report report = loader.create(Report.class);
List<Rule> rules = loader.createAll(Rule.class);
```

The loader also injects itself, so a component can declare a `WeftLoader` constructor parameter
to reach the graph at runtime, for example to build reports over the load order and timings.

## Optional dependencies

A parameter typed `Optional<X>` resolves to an empty `Optional` instead of failing when nothing
provides `X`, including when a `@Provides` getter returns null. This is the natural shape for
soft dependencies like a hook into another plugin that may not be installed.

```java
public SellHandler(Optional<VaultHook> vault) { ... }
```

## Qualifiers

When one type has several implementations or products, `@Qualified` tells them apart. On a
`@Wired` class or a `@Provides` getter it tags what is offered, and on a constructor parameter
it selects the matching tag. The processor checks every qualified dependency at compile time, so
a missing or ambiguous tag fails the build.

```java
@Wired
@Singleton
@Qualified("sql")
public final class SqlStorage implements SpawnerStorage { ... }

@Wired
@Singleton
@Qualified("file")
public final class FileStorage implements SpawnerStorage { ... }

public SpawnerService(@Qualified("sql") SpawnerStorage storage) { ... }
```

The same works for products, so one singleton can expose two values of the same type.

```java
@Provides
public DataSource main() { ... }

@Provides
@Qualified("archive")
public DataSource archive() { ... }
```

A qualified parameter resolves only through its tagged implementation or product. Explicit
arguments, ambient roots, and the loader itself carry no qualifier, so they never satisfy one.

## Lazy singletons

`@Singleton(lazy = true)` defers creation to the first injection instead of building the
component during load. Use it for expensive components that are rarely needed. A lazy singleton
cannot implement `Loader` or carry `@Provides`, `@Initializes`, or `@Requires`, since it has no
slot in the load order.

```java
@Wired
@Singleton(lazy = true)
public final class BackupExporter { ... }
```

## Extra ambient roots

Arguments to `enable` after the registry become ambient roots as well, available to every
component by type.

```java
loader = BukkitWeft.enable(this, WiredComponents.INSTANCE, myExternalService);
```
