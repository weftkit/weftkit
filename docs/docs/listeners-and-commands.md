---
title: "Registering Bukkit listeners and commands"
description: How weftkit registers Bukkit listeners automatically during enable, and how commands are built through the loader and attached in onWeftEnable.
---

# Listeners and commands

Event listeners and commands are where a plugin meets the server, and they are where manual
wiring usually breaks first: a listener that never gets registered fails silently, and a
command handler built before its dependencies exist fails loudly. weftkit removes the first
problem entirely and keeps the second one ordered, so this page covers both halves.

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

weftkit does not register commands for you, because command setup is specific to your plugin.
Build the command object through the loader and attach it to Bukkit yourself.

```java
@Wired
@Singleton
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

Wire it in `onWeftEnable`, where the loader arrives with the graph already up.

```java
getCommand("hello").setExecutor(loader.get(HelloCommand.class));
```

Then declare the command in `plugin.yml`.

```yaml
commands:
  hello:
    description: Greet the sender
```

## Related

- [join-leave-messages](examples/join-leave-messages.md): a runnable plugin around one wired listener
- [player-homes](examples/player-homes.md): wires two commands through the loader this way
- [Components](components.md): constructor injection and the rules behind `@Wired`
- [Modules and feature flags](modules.md): the machinery that registers listeners, and how to gate one behind a flag
- [Lifecycle](lifecycle.md): what has already happened by the time `onWeftEnable` runs
