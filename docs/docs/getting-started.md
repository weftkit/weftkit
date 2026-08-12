---
description: Install weftkit from Maven Central, annotate your plugin main with @Registry, and boot a wired Bukkit plugin in a few lines.
---

# Getting started

## Prerequisites

- Java 17 or newer
- A Bukkit API on your compile classpath (Spigot or Paper) for your server version
- Gradle (the examples use the Kotlin DSL)

## Installation

weftkit is published to Maven Central. Add it to your plugin's Gradle build. The `spigot-api`
dependency comes from the Spigot repository.

```kotlin
repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    implementation("org.weftkit:weftkit-bukkit:0.1.0")
    annotationProcessor("org.weftkit:weftkit-processor:0.1.0")
    annotationProcessor("org.weftkit:weftkit-bukkit:0.1.0")
}
```

`weftkit-bukkit` brings the runtime and the annotations. The two annotation processors validate
the graph and generate the registry during compilation.

## A minimal plugin

Annotate your plugin main with `@Registry`, start weftkit in `onEnable`, and reach your
components through the returned loader.

```java
@Registry
public final class HelloPlugin extends JavaPlugin {

    private WeftLoader loader;

    @Override
    public void onEnable() {
        loader = BukkitWeft.enable(this, WiredComponents.INSTANCE);
        if (loader == null) return;
        getLogger().info(loader.get(Greeter.class).greet("world"));
    }

    @Override
    public void onDisable() {
        BukkitWeft.disable(this, loader);
    }
}
```

```java
@Wired
@Singleton
public final class Greeter {

    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

!!! note "WiredComponents is generated"
    `WiredComponents` is produced by the annotation processor during compilation, in your plugin
    main's package. It does not exist until you build once, so a fresh checkout shows it
    unresolved in the IDE until the first compile. Enable annotation processing in your IDE so it
    regenerates as you edit.

## plugin.yml

weftkit does not change how Bukkit finds your plugin, so declare the main class as usual.

```yaml
name: HelloPlugin
version: 1.0.0
main: com.example.hello.HelloPlugin
api-version: "1.20"
```

## Build

```sh
./gradlew build
```

The processor validates the whole graph as it compiles, so a missing dependency, a cycle, or a
malformed listener stops the build.

## Next steps

- [Components](components.md) covers injection, singletons, products, and reaching your objects.
- [Lifecycle](lifecycle.md) covers startup, shutdown, and load order.
- [Listeners and commands](listeners-and-commands.md) covers Bukkit event and command wiring.
