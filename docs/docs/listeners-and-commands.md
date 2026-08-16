---
description: Bukkit listeners register themselves during enable, and commands are built through the loader and attached in onWeftEnable.
---

# Listeners and commands

## Listeners

Any [`@Wired` component](components.md) that implements `Listener` is registered with the
server automatically during [enable](lifecycle.md#startup). Make it a `@Singleton` so it is registered as a single instance, and
weftkit's compile-time rule checks that each `@EventHandler` method is well formed.

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
