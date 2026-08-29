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

## Related

- [join-leave-messages](examples/join-leave-messages.md): a runnable plugin around one wired listener
- [player-homes](examples/player-homes.md): binds two commands with `@CommandHandler`
- [Components](components.md): constructor injection and the rules behind `@Wired`
- [Modules and feature flags](modules.md): the machinery that registers listeners, and how to gate one behind a flag
- [Lifecycle](lifecycle.md): what has already happened by the time `onWeftEnable` runs
