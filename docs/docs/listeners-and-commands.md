---
title: "Registering Bukkit listeners and commands"
description: How weftkit registers wired Bukkit listeners and commands automatically during enable, so neither needs a registration call in onWeftEnable.
---

# Listeners and commands

Event listeners and commands are where a plugin meets the server, and they are where manual
wiring usually breaks first: a listener that never gets registered fails silently, and a
command handler wired before its dependencies exist fails loudly. weftkit removes both problems.
A wired listener is registered for you, and a wired command is bound to its `plugin.yml` entry
for you, each once the graph is up and its dependencies exist.

## Listeners

Any [`@Wired` component](components.md) that implements `Listener` is registered with the
server automatically during [enable](lifecycle.md#startup), by the bundled listener
[module](modules.md). Make it a `@Singleton` so it is registered as a single instance, and
weftkit's compile-time rule checks that each `@EventHandler` method is well formed. A listener
gated by a [`@FeatureFlag`](modules.md#feature-flags) is only created and registered while its
flag is on.

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
        event.getPlayer().sendMessage(greeter.greet(event.getPlayer().getName()));
    }
}
```

There is no registration call to forget and none to clean up. When the plugin enables, the
bundled listener module collects every wired `Listener` from the graph and registers it with
the server; when the plugin disables, Bukkit drops the registrations along with the plugin.

## Commands

Any [`@Wired` component](components.md) that implements `CommandExecutor` and carries a
`@CommandHandler` naming a `plugin.yml` command is bound to that command automatically during
[enable](lifecycle.md#startup), by the bundled command [module](modules.md). The annotation name
mirrors `@EventHandler`. Make it a `@Singleton`, and a component gated by a
[`@FeatureFlag`](modules.md#feature-flags) is only created and bound while its flag is on. If the
component also implements `TabCompleter`, it is bound for completion too.

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
        sender.sendMessage(greeter.greet(sender.getName()));
        return true;
    }
}
```

There is no `setExecutor` call to write in `onWeftEnable` and none to clean up. Declare the
command in `plugin.yml` as usual, and `@CommandHandler` binds the executor to it.

```yaml
commands:
  hello:
    description: Greet the sender
```

The command name in the annotation is the one declared in `plugin.yml`, without the leading
slash. A name that no `plugin.yml` entry declares is logged and left unbound rather than failing
the plugin. To wire a command by hand instead, leave off `@CommandHandler` and set the executor
yourself in `onWeftEnable` from the loader.

## Paper commands

On Paper 1.21.1 or newer, commands can skip `plugin.yml` and register through Paper's Brigadier
API instead. That is also the only way for plugins with a `paper-plugin.yml`, which cannot
declare commands. Swap the adapter for `weftkit-paper` and its processor, which bring the Bukkit
adapter along:

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    implementation("org.weftkit:weftkit-paper:{{ weftkit_version }}")
    annotationProcessor("org.weftkit:weftkit-processor:{{ weftkit_version }}")
    annotationProcessor("org.weftkit:weftkit-paper-processor:{{ weftkit_version }}")
}
```

Any [`@Wired` component](components.md) that carries `@PaperCommand` is registered by the
bundled `paper:commands` [module](modules.md). It implements one of two interfaces:

- `BasicCommand`, Paper's own, for string arguments
- `CommandTree`, for a full Brigadier tree rooted at the label it is handed

```java
@Wired
@Singleton
@PaperCommand(value = "warps", description = "List every warp", aliases = "warplist")
final class WarpsCommand implements BasicCommand {

    private final WarpStore warps;

    WarpsCommand(WarpStore warps) {
        this.warps = warps;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        source.getSender().sendMessage("Warps: " + String.join(", ", warps.names()));
    }
}
```

```java
@Wired
@Singleton
@PaperCommand("warp")
final class WarpCommand implements CommandTree {

    private final WarpStore warps;

    WarpCommand(WarpStore warps) {
        this.warps = warps;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> tree(String label) {
        return Commands.literal(label)
                .requires(source -> source.getExecutor() instanceof Player)
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::teleport));
    }

    private int teleport(CommandContext<CommandSourceStack> context) {
        Location warp = warps.find(StringArgumentType.getString(context, "name"));
        if (warp == null) return 0;
        ((Player) context.getSource().getExecutor()).teleport(warp);
        return Command.SINGLE_SUCCESS;
    }
}
```

It must be a `@Singleton`, and a [`@FeatureFlag`](modules.md#feature-flags) registers it only
while its flag is on. The processor checks that a component is a singleton, implements exactly
one of the two interfaces, names a lowercase label and aliases free of whitespace, `:` and a
leading `/` that no other command of the plugin claims, and does not also carry `@CommandHandler`.
Commands behind different feature flags may share a label. While both flags are on, the first by
class name registers and the other is left unregistered with a warning.

Paper has no way to unregister a command. When the plugin disables, its commands are hidden
instead: they fail every permission check, so they drop out of completion and stay unusable
until Paper rebuilds its commands.

## Related

- [join-leave-messages](examples/join-leave-messages.md): a runnable plugin around one wired listener
- [player-homes](examples/player-homes.md): binds two commands with `@CommandHandler`
- [warps](examples/warps.md): registers a `CommandTree` and a `BasicCommand` with `@PaperCommand`
- [Components](components.md): constructor injection and the rules behind `@Wired`
- [Modules and feature flags](modules.md): the machinery that registers listeners, and how to gate one behind a flag
- [Lifecycle](lifecycle.md): what has already happened by the time `onWeftEnable` runs
