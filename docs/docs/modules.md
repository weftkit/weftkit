---
description: weftkit modules are constructor-injected singletons woven into the graph at compile time. How to use, disable, configure, and write them.
---

# Modules

A module is a unit of behavior a jar contributes to your plugin: the adapter's listener
registration and weftkit's own metrics are modules, and a library can ship one that brings its
components along. Modules are woven into your generated wiring at compile time as ordinary hard
references, so they load, inject, shade, and minimize exactly like your own components.

A module class is annotated `@WeftModule` and lives in the graph as an eager singleton. It takes
its collaborators through its constructor and may implement `ModuleLifecycle` for hooks that run
once the whole graph is up:

```java
@WeftModule(id = HologramModules.RENDERER)
public final class HologramModule implements ModuleLifecycle {

    private final JavaPlugin plugin;
    private final Weft weft;
    private final List<Hologram> shown = new ArrayList<>();

    public HologramModule(JavaPlugin plugin, Weft weft) {
        this.plugin = plugin;
        this.weft = weft;
    }

    @Override
    public boolean activate() {
        for (Hologram hologram : weft.all(Hologram.class)) {
            hologram.show(plugin);
            shown.add(hologram);
        }
        return true;
    }

    @Override
    public void deactivate() {
        shown.forEach(Hologram::hide);
        shown.clear();
    }
}
```

`Weft` does exactly one thing: `all` enumerates the wired components by supertype, feature
flagged ones excluded. It exists because that set is not statically known; everything else a
module needs, the plugin included, arrives through its constructor. It is also the only graph
handle a component can inject: the `WeftLoader` stays with the plugin main.

## Lifecycle

Modules run inside the normal [lifecycle](lifecycle.md). `activate` fires after every eager
singleton has loaded and `deactivate` runs in reverse order before the graph tears down, on the
same instances, so a module keeps state between the two hooks in plain fields. Returning `false`
from `activate` aborts startup like a failed `load`: already activated modules deactivate in
reverse, the graph unloads, and the plugin is disabled. A module may also implement `Loader` for
work at its own load position.

Activation order follows the dependency graph. A module that must run after another injects it,
and the deactivation order is the exact reverse. There is no separate ordering mechanism.

## Using and disabling modules

The adapter's bundled modules need no declaration. A third-party module joins through the
registry:

```java
@Registry(modules = HologramModule.class)
public final class MyPlugin extends WeftPlugin { ... }
```

Opting out uses the module's id, through the id holder its artifact ships, so switching a module
off never imports it:

```java
@Registry(disable = BukkitModules.METRICS)
public final class MyPlugin extends WeftPlugin { ... }
```

A disabled module and the components it carries are simply absent from the generated wiring, and
an id that matches no module fails the build, so a typo cannot silently keep a module on.

## Module settings

A module takes consumer configuration as a constructor parameter typed to a `@ModuleSettings`
interface with defaulted methods:

```java
@ModuleSettings
public interface HologramSettings {

    default int refreshTicks() {
        return 20;
    }
}
```

Wire an implementation to configure the module, backed by whatever you like:

```java
@Wired
final class MyHologramSettings implements HologramSettings {

    private final JavaPlugin plugin;

    MyHologramSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int refreshTicks() {
        return plugin.getConfig().getInt("holo-refresh", 20);
    }
}
```

Without one, the processor generates an implementation that keeps every default, so the module
always injects a real instance. An interface with a non-defaulted method fails the build until an
implementation is wired.

## Feature flags

`@FeatureFlag` gates a leaf component, typically a listener or command, behind a named flag:

```java
@Wired
@Singleton
@FeatureFlag("holiday-greetings")
final class HolidayListener implements Listener { ... }
```

An off flag gates construction: the component is never created, its `Loader` hooks never run, and
`weft.all` skips it. Because of that, no component may depend on a flagged one, which the
processor enforces. Flags resolve against a `FeatureFlags` implementation, either wired as a
component or passed as an ambient value (the ambient wins); without one, each flag's
`enabledByDefault` decides.

## Publishing a module

A module artifact needs only `weftkit-annotations` on its compile classpath; the consumer's
annotation processor reads the module class straight from the jar. Components the module ships
are ordinary public `@Wired` classes, listed on the annotation so they are woven, and dropped,
together with the module:

```java
@WeftModule(id = HologramModules.RENDERER, components = {HologramStore.class, HologramListener.class})
public final class HologramModule implements ModuleLifecycle { ... }
```

Ship an id holder next to the module (like the adapter's `BukkitModules`) so consumers can
disable it without importing it. An adapter that wants its modules woven into every consuming
plugin automatically registers a `ModuleContributor` service with the annotation processor, the
same way it contributes validation rules; a contributed class missing from the compile classpath
is skipped, so the contribution only applies where the adapter is in use. Declarations on
`@Registry` win over contributions with the same id.

`@Requires` and `@Initializes` are not supported on module components, since the static holder
analysis only runs over sources.

The [announcer example](https://github.com/weftkit/weftkit/tree/main/examples/bukkit/announcer)
is a complete plugin built around such a module: enumeration through `Weft`, an id holder,
settings, and a feature flagged component.
