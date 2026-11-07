---
title: "Scheduler: plugin tasks with a managed lifecycle, on Bukkit, Paper, and Folia"
description: The Scheduler weftkit injects into every component, cancelled as the graph unloads, shaped after Folia's regions so one code path runs on Bukkit, Paper, and Folia.
---

# Scheduler

Every component can inject a `Scheduler`. It is an [ambient root](components.md#ambient-roots)
like the plugin itself and needs no declaration. When the graph unloads, weftkit cancels every
task scheduled through it.

```java
@Wired
@Singleton
final class HomesFile implements Loader {

    private final Scheduler scheduler;

    HomesFile(JavaPlugin plugin, Scheduler scheduler) {
        this.scheduler = scheduler;
        ...
    }

    @Override
    public boolean load() {
        ...
        scheduler.global().runRepeating(this::save, 20L * 60 * 5, 20L * 60 * 5);
        return true;
    }

    @Override
    public void unload() {
        save();
    }
}
```

`unload` does not cancel the autosave task. weftkit cancels it as the graph finishes unloading. The
[player-homes example](examples/player-homes.md) runs exactly this code.

## The API

The interface follows Folia's threaded regions. Every task names the `Region` it runs in or runs
`async()`. On Bukkit and Paper every region is the main thread.

| Region | Bukkit and Paper | Folia |
| --- | --- | --- |
| `global()` | Main thread | Global region scheduler |
| `at(Location)` | Main thread | The region owning the `Location` |
| `on(Entity)` | Main thread | The `Entity`'s own scheduler |
| `async()` | Bukkit's async pool | Folia's async scheduler |

A `Region` offers:

- `run(task)`: runs on the next tick.
- `runLater(task, delayTicks)`: runs after the delay. A delay of `0` is the next tick.
- `runRepeating(task, delayTicks, periodTicks)`: runs every period after the delay.
- `isCurrentThread()`: whether the current thread ticks this region.

`async()` offers the same three scheduling methods with `Duration`s instead of ticks. Bukkit
rounds a `Duration` up to whole ticks. Folia keeps millisecond precision.

```java
scheduler.at(location).run(() -> world.spawnParticle(...));
scheduler.on(player).runLater(() -> player.sendMessage("..."), 20);
scheduler.async().run(this::saveToDisk);
```

Every scheduling method returns a `Task` handle with `cancel()` and `isCancelled()`. On Folia, a
task whose entity was removed before it ran reports cancelled.

Check threads with `isCurrentThread()`, not `Bukkit.isPrimaryThread()`. Folia has no single main
thread.

## Lifecycle

On disable, weftkit tears down in this order:

1. The [modules](modules.md) deactivate.
2. The singletons unload, in reverse creation order.
3. The bundled `bukkit:scheduler` [module](modules.md), loaded first and so unloaded last, cancels
   every task scheduled through the `Scheduler`.

A module that starts a repeating task in `activate` cancels it in `deactivate`.
The [announcer example](examples/announcer.md) does this.

When startup aborts because a `load` hook failed, the same unload runs for the components that
did load, whichever `Scheduler` the graph runs with, so an aborted enable leaves no task running.
An `unload` hook may still see its own tasks queued, the module cancels them right after. Disable
the module with `@Registry(disable = BukkitModules.SCHEDULER)` to cancel by hand.

`cancelAll` belongs to the adapter and only touches tasks scheduled through the `Scheduler`.
Cancel your own tasks through their `Task` handles. A task scheduled directly with the platform
scheduler stays the plugin's to cancel.

## Folia

Any weftkit plugin runs on Folia after two changes. This applies to `plugin.yml` and
`paper-plugin.yml` plugins alike.

1. Add `weftkit-folia` to the dependencies that go into the jar.

    ```kotlin
    dependencies {
        implementation("org.weftkit:weftkit-folia:{{ weftkit_version }}")
    }
    ```

2. Declare `folia-supported: true` in `plugin.yml` or `paper-plugin.yml`.

With `weftkit-folia` on the compile classpath, the Bukkit processor weaves the `folia:scheduler`
[module](modules.md) into the registry. The module's `SchedulerDetection` component
[provides](components.md#products-with-provides) the `Scheduler`. It picks Folia's regions on
Folia and the Bukkit scheduler on every other server. On servers other than Folia, no Folia class
is loaded.

`weftkit-folia` requires Folia 1.20.1 or newer.

Without `weftkit-folia`, a plugin that enables on Folia fails with this message:

```
Folia detected. Add weftkit-folia to the plugin's dependencies, or pass a Scheduler to BukkitWeft.enable
```

Three ways exist to override the detected scheduler:

- A `scheduler()` override in the plugin main replaces it.
- A scheduler passed to `BukkitWeft.enable` replaces it.
- `@Registry(disable = FoliaModules.SCHEDULER)` drops the module.

`BukkitWeft.scheduler(loader)` returns the scheduler a loader runs with.

The `Scheduler` leaves out two parts of Folia's API. Use Paper's API directly for them:

- A custom retired callback for entity tasks. weftkit's callback marks the `Task` cancelled.
- Region tasks addressed by world and chunk coordinates instead of a `Location`.

The bundled [metrics](metrics.md) module also schedules through the `Scheduler`.

## Related

- [Lifecycle](lifecycle.md): where cancellation sits in startup and shutdown
- [Components](components.md): ambient roots
- [player-homes](examples/player-homes.md): an autosave task left to weftkit to cancel
- [announcer](examples/announcer.md): a `plugin.yml` plugin that also runs on Folia
- [warps](examples/warps.md): a `paper-plugin.yml` plugin that does the same
