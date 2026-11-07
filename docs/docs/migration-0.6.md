---
title: "Migrating from weftkit 0.5 to 0.6"
description: Update processor dependencies, moved imports, metrics settings, ambient declarations, and lifecycle cleanup for weftkit 0.6.
---

# Migrating from 0.5 to 0.6

0.6 changes the compile-time API and lifecycle contracts. Update all weftkit artifacts to
the same version, regenerate the wiring, and rebuild your plugin jar. Existing generated
registries must not be carried across the upgrade.

## Build dependencies

For Bukkit, replace the old processor configuration with:

```kotlin
dependencies {
    implementation("org.weftkit:weftkit-bukkit:{{ weftkit_version }}")
    annotationProcessor("org.weftkit:weftkit-processor:{{ weftkit_version }}")
    annotationProcessor("org.weftkit:weftkit-bukkit-processor:{{ weftkit_version }}")
}
```

Keep your server API as `compileOnly`. Remove `weftkit-bukkit` from the annotation processor
path; its compile-time rules now live in `weftkit-bukkit-processor`. If you depend directly on
`weftkit-annotations`, replace it with `weftkit-api`. Most plugins receive the API transitively.

For Paper's Brigadier commands, use `weftkit-paper` and `weftkit-paper-processor` in place of
the Bukkit pair, keeping `weftkit-processor`. Paper support needs Java 21 and Paper 1.21.1
or newer. Add `implementation("org.weftkit:weftkit-folia:{{ weftkit_version }}")` to support
Folia's scheduler; this works with either adapter. Review your plugin's own thread safety
before declaring `folia-supported: true`.

## Moved imports

| Previous import | New import |
| --- | --- |
| `org.weftkit.wiring.runtime.ComponentRegistry` | `org.weftkit.wiring.registry.ComponentRegistry` |
| `org.weftkit.wiring.runtime.Dependency` | `org.weftkit.wiring.registry.Dependency` |
| `org.weftkit.wiring.runtime.Registries` | `org.weftkit.wiring.registry.Registries` |
| `org.weftkit.wiring.runtime.ComponentLoadException` | `org.weftkit.wiring.runtime.exception.ComponentLoadException` |
| `org.weftkit.wiring.runtime.ResolutionException` | `org.weftkit.wiring.runtime.exception.ResolutionException` |

The existing annotations and `Loader` retain their `org.weftkit.wiring` imports.
`FeatureFlags`, introduced during 0.6 development, lives in `org.weftkit.wiring`; update any
snapshot consumer importing it from the runtime package.

## Registration and graph access

`WeftPlugin` still drives enable and disable. `BukkitWeft.enable` now activates bundled
modules, including listener and command registration. Remove calls to
`BukkitWeft.registerListeners`; that method was removed. Manual integrations still use
`BukkitWeft.enable` and `BukkitWeft.disable` together. If driving the neutral `WeftLoader`
directly, activate modules after a successful load and deactivate them before unloading.

A component can no longer inject `WeftLoader`. Inject its concrete dependencies, `Weft` to
enumerate components, or `WeftView` to inspect the graph. Keep the loader in the plugin main.
See [modules](modules.md) for activation and module configuration.

## Declare ambient values

Extra values passed to `WeftLoader`, `BukkitWeft.enable`, or `ambientValues()` must match a
declared ambient type. Declare abstractions your plugin supplies explicitly:

```java
@Registry(ambient = Storage.class)
public final class MyPlugin extends WeftPlugin {
    // registry() and ambientValues() as usual
}
```

The registry class and its constructor parameter types are declared automatically. The
Bukkit processor declares platform roots, including `JavaPlugin` and `Scheduler`;
`FeatureFlags` is always implicit. Matching uses declared types, so an implementation does
not implicitly expose all its other interfaces. Undeclared or duplicate values fail during
loader construction. A declared abstraction can override a wired implementation with a test
fake; its eager implementation still loads. See [ambient roots](components.md#ambient-roots).

## Replace metrics annotations

Remove `@WeftMetrics` and `@NoMetrics`. To configure reporting, wire a `MetricsSettings`
implementation:

```java
@Wired
@Singleton
final class PluginMetricsSettings implements MetricsSettings {
    @Override
    public boolean reportName() {
        return true; // Only if the plugin name should be reported.
    }
}
```

`MetricsSettings.enabled()` replaces `@WeftMetrics(enabled = ...)`; `reportName()` replaces
its name-reporting option. To disable the entire metrics module, use
`@Registry(disable = BukkitModules.METRICS)` in place of `@NoMetrics`.

bStats is no longer bundled. Add and relocate `org.bstats:bstats-bukkit` explicitly if you
want metrics, and set `MetricsSettings.serviceId()` for your plugin's own charts. Without
bStats, weftkit transmits no metrics. See [metrics](metrics.md) for the complete configuration.

## Review failure cleanup and creation

- A constructed singleton whose `load()` returns false or throws now receives `unload()`
  immediately. Make cleanup safe after partial initialization; move any manual rollback out
  of `load()` or make it idempotent. Constructors that throw still own their cleanup.
- A module whose `activate()` fails receives `deactivate()` too. Undo partial registrations
  and tolerate incomplete activation.
- A throwing plugin enable hook tears down the graph and disables the plugin. A throwing
  disable hook still runs framework teardown and clears the loader.
- Do not call `load()` on an already loaded graph. Unload first. Calling `unload()` from a
  singleton constructor, load hook, or another unload hook is rejected.
- `create()` rejects arguments for singletons. For plain components, the first assignable
  argument wins when several match; unused arguments are ignored.
- Lazy load failures remain failed until `resetFailure(type)` or unload. Retrying a repaired
  dependency therefore requires an explicit reset.
- Products from `@Provides` are captured after the provider loads. Initialize them before
  returning success; a null product fails startup.
- Components generated by another annotation processor after registry generation now fail
  with a diagnostic. Generate those types in a separate step before compilation.

A lazy singleton's hook runs on the thread that first requests it. Check thread affinity and
avoid blocking on the server thread from that hook. For automatic task cancellation at
teardown, inject the [Scheduler](scheduler.md).

## Verify the upgrade

Run a clean build once after updating dependencies and imports, then exercise successful
startup, failed startup, and shutdown on your supported servers. Check the shaded jar for
relocated weftkit classes, the plugin descriptor, and third-party licenses. Before publishing a
release, build an independent plugin against the candidate artifacts to verify processor
discovery and transitive dependencies alongside the repository examples.
