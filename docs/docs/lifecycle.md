---
title: "Plugin lifecycle: load order, hooks, and shutdown"
description: How weftkit starts a Bukkit plugin in dependency order, tears it down in reverse, renders the dependency graph, and why it has no reload.
---

# Lifecycle

weftkit owns your plugin from `onEnable` to `onDisable`. Extend `WeftPlugin` and the whole
cycle runs itself, with `BukkitWeft.enable` and `BukkitWeft.disable` as the underlying calls for
plugins that want manual control.

## Startup

`BukkitWeft.enable` builds the loader and brings up every [`@Singleton`](components.md) in
dependency order. As each singleton is created, if it implements `Loader` its `load` method
runs. Returning `false`
from `load` aborts startup: weftkit tears down everything it has already loaded, in reverse
order, and disables the plugin, so you never run in a half initialized state. The component
whose `load()` returned false or threw receives `unload()` immediately, before its dependencies
are torn down. Cleanup must tolerate a partially completed load. A constructor that throws
must release its own resources, since no instance exists for weftkit to clean up. Once every
singleton has loaded, the [modules](modules.md) activate in load order; the bundled listener
module registers each `@Wired` [listener](listeners-and-commands.md) with the server. A module
that aborts activation triggers the same full teardown as a failed `load`. Its `deactivate`
hook runs too, so modules must tolerate and undo partially completed activation.

```java
@Override
protected void onWeftEnable(WeftLoader loader) {
    // Runs once every singleton is up and the listeners are registered. When a component
    // aborts startup, weftkit disables the plugin and this hook never runs.
}
```

## Threading

Loading belongs to the thread that created the loader, on Bukkit the server main thread:

- `load` throws from any other thread.
- While `load` runs, and before it or after `unload`, every other call throws from any other
  thread too.
- Views of the immutable registry, such as `loadOrder` or `contains`, work from anywhere at any
  time.

Once `load` has returned true, any thread may call `get`, `create`, `Weft.all`, the view methods,
the load timings, `resetFailure`, and `unload`. Reads are lock-free, and plain components and
loaded lazy singletons resolve without waiting on a materialization in progress. Singleton
creation is serialized inside the loader, so a lazy singleton materializes exactly once even when
two threads demand it at the same moment: one builds, the other waits for the instance.

A lazy `load` hook therefore runs on whichever thread asked first, in an async task or on a Folia
region thread as easily as on the main thread. Inside it, never assume the main thread, and never
block on the main thread or on another thread's graph access, not even through
`callSyncMethod`: `unload` runs on the main thread and waits for the materialization in
progress, so both would wait forever. A singleton that needs the main thread should be eager.
`unload` waits for that materialization and for nothing else, so stop async work before
disabling.
An async materialization that arrives after the unload throws instead of reviving the graph,
and `get` never hands out a singleton whose `load` hook has not completed.

Unload does not wait for cache reads or prototype creation already in progress. They may finish
with dependencies whose unload hooks have run; coordinate your own work if those resources must
stay alive. During teardown, only the unloading thread may start new graph calls.

## Load order

Load order follows the dependency graph. A component loads after every component it depends on,
so a singleton can rely on its dependencies being fully loaded inside its own `load`. The
[modules](modules.md) and their dependencies load before every other component. A value
exposed with `@Provides` is captured right after its owner loads and is injectable from then on.
A getter that still returns null at that point fails startup, so a forgotten field surfaces
during load instead of at the first injection. For ordering that is not expressed through
constructor dependencies, `@Initializes` and `@Requires` place a component after the singleton
that sets up the static holders it reads. Holders are classes marked `@StaticHolder`, and reading
one during construction or load without declaring `@Requires` fails the build.

## Shutdown

On disable, the modules deactivate in reverse activation order, the listener module
unregistering exactly the listeners it registered. weftkit then runs `unload` on every loaded
singleton in reverse creation order, lazy singletons interleaved where they materialized, so a
component is torn down before the ones it depended on, and every captured `@Provides` value is
dropped. The scheduler module unloads last and cancels every task scheduled through the
[`Scheduler`](scheduler.md). Teardown keeps going even if one
component's `unload` throws, and the failures are reported together at the end. This includes
unchecked errors: startup keeps the original failure and suppresses rollback failures; shutdown
rethrows the first failure after attempting the remaining hooks. Teardown is safe
when startup aborted or never ran. Calling `unload()` from a singleton constructor, a load
hook, or another unload hook is rejected; teardown must run outside those callbacks.

```java
@Override
protected void onWeftDisable(WeftLoader loader) {
    // Runs before teardown, with every component still resolvable
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

Inject [`WeftDiagnostics`](diagnostics.md) for timings and component state. Manual integrations
can also query the loader directly. `loadOrder` returns the singleton load
sequence, `totalLoadTime` returns the combined startup cost for a one-line enable log, and
`loadTimings` returns how long each singleton took to construct and load, in load
order, as a snapshot that is safe to hand to another thread.

```java
loader.loadTimings().forEach((type, duration) ->
        getLogger().info(type.getSimpleName() + " loaded in " + duration.toMillis() + "ms"));
```

The processor also writes the full dependency graph as `weftkit-graph.dot` next to the generated
registry sources (under `build/generated/sources/annotationProcessor`). Render it with
[Graphviz](https://graphviz.org/) to see your plugin's wiring, where every solid or dashed edge
points at a dependency:

```sh
dot -Tsvg weftkit-graph.dot -o weftkit-graph.svg
```

The [player-homes example](examples/player-homes.md) renders to this, its two commands sharing
the `HomeStore` singleton, which loads homes through the package-private `HomesFile`, alongside
the bundled listener, command, and metrics modules. The command module picks up both commands:

<figure class="wk-graph" markdown>
![Dependency graph of the player-homes plugin. HomeCommand and SetHomeCommand, both amber singletons, point at the HomeStore singleton, which points at the dashed-bordered package-private HomesFile. The teal CommandModule points at both commands with dotted edges labelled @CommandHandler, the teal MetricsModule points at a plain WeftMetricsSettingsDefaults, the teal SchedulerModule points at the ambient Scheduler, and the teal ListenerModule stands alone.](assets/diagrams/weftkit-graph.svg)
<figcaption>The generated <code>weftkit-graph.dot</code> for the player-homes example, rendered with Graphviz.</figcaption>
</figure>

The nodes and edges carry a small visual vocabulary:

| Element | Meaning |
| --- | --- |
| Indigo box | A plain `@Wired` component, created fresh for every injection. |
| Amber box, thicker border | A `@Singleton`, created once. |
| Teal box, thicker border | A [module](modules.md), an eager singleton with activation hooks. |
| Grey dashed box | A static holder ordered through `@Requires` / `@Initializes`. |
| Dashed border | A [package-private component](internal-components.md), kept off your public surface. |
| `feature: name` badge | The component is gated by a `@FeatureFlag`; the badge names the flag. |
| `inert: no Module` badge | The component carries a `@HandledBy` annotation whose module the registry lacks. |
| Solid edge | A constructor dependency. |
| Dashed edge | A `@Requires` ordering edge, amber when it points at the holder's initializer. |
| Dotted teal edge | A module handling the component through the labelled `@HandledBy` annotation. |

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
        // release resources here, runs in reverse creation order
    }
}
```

The [player-homes example](examples/player-homes.md) runs this cycle end to end: it loads
homes from disk in `load`, aborts startup on a corrupt file, and saves in `unload`.

## Related

- [Diagnostics](diagnostics.md): injectable graph snapshots and an optional command
- [Components](components.md): what gets loaded, and how the order is decided
- [Modules and feature flags](modules.md): module activation inside the lifecycle
- [Scheduler](scheduler.md): tasks cancelled by the lifecycle instead of by hand
- [Troubleshooting](troubleshooting.md): the errors the processor raises for broken graphs
- [player-homes](examples/player-homes.md): the lifecycle end to end in a runnable plugin
