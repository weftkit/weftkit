---
description: Bukkit listeners register themselves during enable, and commands are built through the loader and attached in onEnable.
---

# Listeners and commands

## Listeners

Any `@Wired` component that implements `Listener` is registered with the server automatically
during `BukkitWeft.enable`. Make it a `@Singleton` so it is registered as a single instance, and
weftkit's compile-time rule checks that each `@EventHandler` method is well formed.

```java
@Wired
@Singleton
public final class JoinListener implements Listener {

    private final Greeter greeter;

    public JoinListener(Greeter greeter) {
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
public final class HelloCommand implements CommandExecutor {

    private final Greeter greeter;

    public HelloCommand(Greeter greeter) {
        this.greeter = greeter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(greeter.greet(sender.getName()));
        return true;
    }
}
```

Wire it in `onEnable`, after `enable` has returned the loader.

```java
getCommand("hello").setExecutor(loader.get(HelloCommand.class));
```

Then declare the command in `plugin.yml`.

```yaml
commands:
  hello:
    description: Greet the sender
```
