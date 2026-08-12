---
description: How weftkit starts a Bukkit plugin in dependency order, tears it down in reverse, renders the dependency graph, and why it has no reload.
---

# Lifecycle

weftkit owns your plugin from `onEnable` to `onDisable`. `BukkitWeft.enable` starts everything up
and `BukkitWeft.disable` shuts it down.

## Startup

`BukkitWeft.enable` builds the loader and brings up every `@Singleton` in dependency order. As
each singleton is created, if it implements `Loader` its `load` method runs. Returning `false`
from `load` aborts startup: weftkit tears down everything it has already loaded, in reverse
order, and disables the plugin, so you never run in a half initialized state. Once every
singleton has loaded, weftkit registers each `@Wired` listener with the server.

```java
@Override
public void onEnable() {
    loader = BukkitWeft.enable(this, WiredComponents.INSTANCE);
    if (loader == null) return;
}
```

## Load order

Load order follows the dependency graph. A component loads after every component it depends on,
so a singleton can rely on its dependencies being fully loaded inside its own `load`. A value
exposed with `@Provides` is captured right after its owner loads and is injectable from then on.
A getter that still returns null at that point fails startup, so a forgotten field surfaces
during load instead of at the first injection. For ordering that is not expressed through
constructor dependencies, `@Initializes` and `@Requires` place a component after the singleton
that sets up the static holders it reads. Holders are classes marked `@StaticHolder`, and reading
one during construction or load without declaring `@Requires` fails the build.

## Shutdown

`BukkitWeft.disable` runs `unload` on every loaded singleton in reverse load order, so a
component is torn down before the ones it depended on, and every captured `@Provides` value is
dropped. Teardown keeps going even if one
component's `unload` throws, and the failures are reported together at the end. `disable` is safe
to call when startup aborted or never ran.

```java
@Override
public void onDisable() {
    BukkitWeft.disable(this, loader);
}
```

## Reloading

weftkit deliberately has no framework level reload. Recreating components at runtime cannot fix
references captured outside the graph, like scheduled tasks, command executors, or other plugins
holding your API service, and those stale references are exactly the bugs that made `/reload`
infamous. Reload in place instead: give the singleton that owns the state a `reload` method and
let dependents read through it, so nothing ever goes stale.

```java
@Wired
@Singleton
public final class Config implements Loader {

    private volatile Settings settings;

    @Override
    public boolean load() {
        return reload();
    }

    public boolean reload() {
        Settings parsed = parse();
        if (parsed == null) return false;
        settings = parsed;
        return true;
    }

    public Settings settings() {
        return settings;
    }
}
```

A reload command is then one line: `loader.get(Config.class).reload()`. Components that injected
`Config` call `settings()` when they need values and always see the current state.

## Diagnostics

The loader exposes what happened during startup. `loadOrder` returns the singleton load
sequence, and `loadTimings` returns how long each singleton took to construct and load, in load
order.

```java
loader.loadTimings().forEach((type, duration) ->
        getLogger().info(type.getSimpleName() + " loaded in " + duration.toMillis() + "ms"));
```

The processor also writes the full dependency graph as `weftkit-graph.dot` next to the generated
registry sources (under `build/generated/sources/annotationProcessor`). Render it with Graphviz
to see your plugin's wiring: singletons are boxes, and every edge points at a dependency.

```sh
dot -Tsvg weftkit-graph.dot -o weftkit-graph.svg
```

## The Loader hook

Implement `Loader` on any singleton that needs to do work at startup or shutdown. `load` runs as
the singleton is created and `unload` runs on shutdown. Only singletons may implement `Loader`,
since a per-injection component would never have its `load` called.

```java
@Wired
@Singleton
public final class Metrics implements Loader {

    @Override
    public boolean load() {
        // start up here, return false to abort the plugin
        return true;
    }

    @Override
    public void unload() {
        // release resources here, runs in reverse load order
    }
}
```
