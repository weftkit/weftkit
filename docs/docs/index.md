---
title: weftkit
description: A managed lifecycle for Bukkit, Spigot, and Paper Minecraft plugins, with dependency injection wired at compile time so mistakes fail the build, not the server.
hide:
  - toc
---

<div class="wk-hero wk-hero-gradient" markdown>

# <img src="assets/logo/svg/weftkit-mark-weave.svg" alt=""> <span class="wk-weft">weft</span><span class="wk-kit">kit</span> runs your Bukkit plugin's lifecycle

weftkit manages your plugin's lifecycle at runtime and resolves the wiring that drives it at
compile time, so the dependency graph that survives `javac` is the one that runs on the server.

[Get started](getting-started.md){ .md-button .md-button--primary }
[Browse the examples](examples/index.md){ .md-button }

</div>

Four classes make a plugin that welcomes joining players and greets anyone who runs `/hello`.
Components declare their dependencies as constructor parameters, and weftkit builds them, injects
them, and registers the listener and the command. No `registerEvents`, no `setExecutor`, no
factories, no reflection.

<div class="wk-code" markdown>

=== "Plugin main"

    ```java
    @Registry
    public final class HelloPlugin extends WeftPlugin {

        @Override
        protected ComponentRegistry registry() {
            return WeftWiring.INSTANCE;
        }
    }
    ```

    The only wiring you write: hand back the registry weftkit generates.

=== "Component"

    ```java
    @Wired
    @Singleton
    final class Greeter {

        public String greet(CommandSender who) {
            return "Welcome, " + who.getName();
        }
    }
    ```

    A plain singleton. weftkit builds it and injects it wherever it is asked for.

=== "Listener"

    ```java
    @Wired
    @Singleton
    final class JoinListener implements Listener {

        private final Greeter greeter;

        JoinListener(Greeter greeter) {
            this.greeter = greeter;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            event.getPlayer().sendMessage(greeter.greet(event.getPlayer()));
        }
    }
    ```

    Registered for you. Its dependency arrives through the constructor.

=== "Command"

    ```java
    @Wired
    @Singleton
    @CommandHandler("hello")
    final class HelloCommand implements CommandExecutor {

        private final Greeter greeter;

        HelloCommand(Greeter greeter) {
            this.greeter = greeter;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            sender.sendMessage(greeter.greet(sender));
            return true;
        }
    }
    ```

    `@CommandHandler` binds it to the `hello` entry in `plugin.yml`. The same `Greeter`, injected again.

</div>

## And when the wiring is wrong

The processor validates the whole dependency graph as it compiles. Forget an annotation,
introduce a cycle, or depend on something nothing provides, and the build stops with a message
pointing at the offending class, long before a server ever loads the jar.

<div class="wk-terminal">
<div class="highlight"><pre><code>$ ./gradlew build

JoinListener.java:9: <span class="wk-term-error">error:</span> Dependency must be @Wired or a @Provides product: com.example.hello.Greeter
    JoinListener(Greeter greeter) {
                 <span class="wk-term-caret">^</span>
1 error</code></pre></div>
</div>

[Troubleshooting](troubleshooting.md) lists every error the processor can raise and how to fix
it.

## See your plugin's structure

<figure class="wk-graph" markdown>
![Dependency graph of the player-homes example: the amber HomeCommand and SetHomeCommand singletons point at the HomeStore singleton, which points at the dashed package-private HomesFile, while the teal MetricsModule points at WeftMetricsSettingsDefaults and the teal ListenerModule and CommandModule stand alone.](assets/diagrams/weftkit-graph.svg)
<figcaption markdown="span">The processor also [renders the whole dependency graph](lifecycle.md#diagnostics) as Graphviz. Every component and every edge, package-private classes included. This is the [player-homes](examples/player-homes.md) example.</figcaption>
</figure>

<section class="wk-panel" markdown>

## What you get

<div class="grid cards" markdown>

-   :material-sync:{ .lg .middle } __[Managed lifecycle](lifecycle.md)__

    ---

    Components load in dependency order, run startup and teardown hooks, and shut down cleanly in reverse.

-   :material-puzzle:{ .lg .middle } __[Bukkit integration](listeners-and-commands.md)__

    ---

    `onEnable`, `onDisable`, listener registration, and shutdown are handled for you on any Bukkit server.

-   :material-toggle-switch-outline:{ .lg .middle } __[Modules and feature flags](modules.md)__

    ---

    Features ship as modules that discover their content by enumeration, and flags toggle whole components off.

-   :material-eye-off:{ .lg .middle } __[Internals stay internal](internal-components.md)__

    ---

    Package-private classes are wired like any other component, so injection never forces your internals public.

</div>

</section>

## Why weftkit

A general purpose container hands you an object graph and stops there. weftkit is built around
the plugin lifecycle instead: load order, startup and teardown hooks, and clean shutdown in
reverse are first-class, and listener and command registration on any Bukkit server, Spigot to
Paper, comes with them. That is the part Dagger and Guice leave for you to wire by hand. If you
are weighing options, see [how weftkit compares to Dagger, Guice, and wiring by hand](comparison.md).

!!! note "Pre-release"
    weftkit is pre-1.0, so the public API can still change between releases until 1.0.

## Add it to your build

weftkit is published to Maven Central. One dependency carries the runtime and annotations; the
two annotation processors validate the graph and generate the registry as you compile.

```kotlin
dependencies {
    implementation("org.weftkit:weftkit-bukkit:{{ weftkit_version }}")
    annotationProcessor("org.weftkit:weftkit-processor:{{ weftkit_version }}")
    annotationProcessor("org.weftkit:weftkit-bukkit-processor:{{ weftkit_version }}")
}
```

The full build, including the Bukkit API dependency and repositories, is in
[getting started](getting-started.md#installation).

<section class="wk-panel" markdown>

## Learn from complete plugins

The [examples](examples/index.md) are complete runnable plugins, each documented class by
class with the source embedded.

<div class="grid cards wk-cards-3" markdown>

-   :material-hand-wave:{ .lg .middle } __[join-leave-messages](examples/join-leave-messages.md)__

    ---

    The smallest useful plugin: one wired listener, one injected component, three classes total.

-   :material-home:{ .lg .middle } __[player-homes](examples/player-homes.md)__

    ---

    The lifecycle end to end: load homes from disk at startup, abort on corrupt data, save on shutdown.

-   :material-bullhorn:{ .lg .middle } __[announcer](examples/announcer.md)__

    ---

    A module that discovers its content through enumeration, with settings and a feature flagged component.

</div>

</section>

## FAQ

<!-- faq -->


<div class="wk-cta" markdown>

<div class="wk-cta-text" markdown>

## Ready to wire your plugin?

Add one dependency, hand back the generated registry, and let the build catch the mistakes.

</div>

<div class="wk-cta-actions" markdown>

[View on GitHub](https://github.com/weftkit/weftkit){ .md-button }
[Get started](getting-started.md){ .md-button .md-button--primary }

</div>

</div>
