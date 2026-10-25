<p align="center">
  <a href="https://weftkit.org">
    <img src="https://cdn.jsdelivr.net/gh/weftkit/assets@1/logo/png/weftkit-wordmark.png" alt="weftkit" width="440">
  </a>
</p>

# weftkit runs your Bukkit plugin's lifecycle

<p align="center">
  Lifecycle and dependency injection framework for Bukkit Minecraft plugins, wired at compile time.
</p>

<p align="center">
  <a href="https://github.com/weftkit/weftkit/actions/workflows/build.yml"><img src="https://github.com/weftkit/weftkit/actions/workflows/build.yml/badge.svg" alt="Build"></a>
  <a href="https://central.sonatype.com/artifact/org.weftkit/weftkit-bukkit"><img src="https://img.shields.io/maven-central/v/org.weftkit/weftkit-bukkit?label=Maven%20Central" alt="Maven Central"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License: Apache 2.0"></a>
  <img src="https://img.shields.io/badge/Java-17%2B-orange.svg" alt="Java 17+">
</p>

> [!NOTE]
> weftkit is pre-1.0, so the public API can still change between releases until 1.0.

weftkit runs your plugin's lifecycle. It brings your components up in dependency order, runs
their startup and shutdown hooks, registers your Bukkit listeners, and tears everything down in
reverse when the plugin disables, aborting cleanly if a component fails to start. The wiring
behind it is resolved at compile time. An annotation processor validates the whole dependency
graph during `javac`, so a missing dependency, a cycle, or a misused annotation fails the build
instead of crashing on startup, and the generated registry keeps the hot path free of reflection.

Full documentation lives at [weftkit.org](https://weftkit.org).

## Quick start

Add weftkit to your plugin's Gradle build. `weftkit-bukkit` carries the runtime and the
annotations. The two annotation processors validate the graph and generate the registry
during compilation:

```kotlin
dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    implementation("org.weftkit:weftkit-bukkit:0.6.0")
    annotationProcessor("org.weftkit:weftkit-processor:0.6.0")
    annotationProcessor("org.weftkit:weftkit-bukkit-processor:0.6.0")
}
```

Annotate your plugin's main class with `@Registry`, extend `WeftPlugin`, and point it at the
generated `WeftWiring`. The plugin itself becomes injectable, `@Wired` listeners are registered
for you, and shutdown tears everything down in reverse load order:

```java
@Registry
public final class HelloPlugin extends WeftPlugin {

    @Override
    protected ComponentRegistry registry() {
        return WeftWiring.INSTANCE;
    }
}
```

Components are plain classes. `@Wired` makes one injectable, and `@Singleton` creates it
once during load and injects it by type from then on. Implement `Loader` to hook the lifecycle
(returning `false` from `load` aborts startup and disables the plugin), and expose a value
to the rest of the graph with `@Provides`. The value is captured right after `load`, so a
getter that still returns null at that point fails startup instead of the first injection:

```java
@Wired
@Singleton
final class Config implements Loader {

    private final HelloPlugin plugin;
    private Greeting greeting;

    Config(HelloPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean load() {
        plugin.saveDefaultConfig();
        greeting = new Greeting(plugin.getConfig().getString("greeting", "Hello"));
        return true;
    }

    @Override
    public void unload() {
        greeting = null;
    }

    @Provides
    public Greeting greeting() {
        return greeting;
    }
}

record Greeting(String text) {}
```

Listeners are components too. weftkit registers every `@Wired` listener, and the
compile-time rule checks that each `@EventHandler` is well-formed:

```java
@Wired
@Singleton
final class JoinListener implements Listener {

    private final Greeting greeting;

    JoinListener(Greeting greeting) {
        this.greeting = greeting;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(greeting.text() + ", " + event.getPlayer().getName());
    }
}
```

No manual registration and no service locator: `javac` fails the build if a dependency is
missing, the graph has a cycle, or a listener declares a malformed handler.

Components do not have to be public. weftkit wires package-private classes too, so your
internals stay internal while still being injected, tested, and lifecycle-managed - see
[internal components](https://weftkit.org/internal-components/).

## Metrics

weftkit ships no telemetry by default: it does not bundle [bStats](https://bstats.org), and a
jar built without it transmits nothing. Adding bStats to your build enables your plugin's own
charts through the wired metrics API, and weftkit's anonymous usage reporting (plugin counts,
weftkit versions, standard server stats) rides along, submitted once per server no matter how
many weftkit plugins run on it. Plugin names are only reported on explicit opt-in, server owners
opt out through the global bStats config. [Plugin charts, what is collected, and how to opt
out](https://weftkit.org/metrics/).

## Why "weftkit"

A *weft* is the horizontal thread passed back and forth through the vertical warp threads in
weaving, binding the loose strands into a single fabric. That is exactly what weftkit does:
it threads through your independent components and binds them into one coherent, working
plugin - a kit for weaving a plugin together.

## Used by

weftkit was extracted from [SilkSpawners](https://github.com/CorneliusMa/SilkSpawners_v2), which
runs on it as its wiring and lifecycle framework.

## Modules

| Module | Description |
| --- | --- |
| `weftkit-api` | The wiring API. The vocabulary - `@Registry`, `@Wired`, `@Singleton`, `@Provides`, `@Qualified`, `@Initializes`, `@Requires`, `@StaticHolder` - the `Loader` and `ModuleLifecycle` lifecycle interfaces and the `FeatureFlags` hook, plus the `ComponentRegistry` contract the processor generates against. Depends on nothing. |
| `weftkit-runtime` | The runtime. `WeftLoader` instantiates components in dependency order, injects by type, and drives the `Loader` lifecycle, working against the `ComponentRegistry` the processor generates. |
| `weftkit-processor` | The platform-neutral annotation processor that validates the graph at compile time and generates the registry. Platform-specific checks are contributed through the `ComponentRule` service interface. |
| `weftkit-bukkit` | The Bukkit adapter runtime. `WeftPlugin` runs the whole lifecycle from `onEnable` to `onDisable` (with `BukkitWeft` as the manual entry point), plus the bundled listener, command, and metrics modules. |
| `weftkit-bukkit-processor` | The Bukkit adapter's compile-time side: contributes the bundled modules to every generated registry and validates `Listener` and `@CommandHandler` components. Goes on the `annotationProcessor` path, never in the jar. |
| `weftkit-paper` | The Paper adapter runtime, on top of `weftkit-bukkit`: the bundled `paper:commands` module registers `@PaperCommand` components with Paper's Brigadier registrar, no `plugin.yml` entry needed. Paper 1.21.1 or newer. |
| `weftkit-paper-processor` | The Paper adapter's compile-time side: brings in `weftkit-bukkit-processor`, contributes the Paper modules, and validates `@PaperCommand` components. Goes on the `annotationProcessor` path, never in the jar. |

## License

Licensed under the [Apache License 2.0](LICENSE).
