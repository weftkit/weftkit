---
title: "Metrics: what bStats collects and how to opt out"
description: weftkit reports anonymous usage numbers to bStats. This page lists exactly what is collected and how plugin authors and server owners opt out.
---

# Metrics

weftkit collects anonymous usage statistics through [bStats](https://bstats.org), the standard
metrics service for Bukkit plugins. The numbers show which weftkit versions are actually in use
and how many plugins run on them, which guides deprecations and compatibility decisions.

## What is collected

On a server running weftkit-based plugins, the first such plugin to enable submits, once per
bStats interval:

- how many plugins on the server ship weftkit
- which weftkit versions those plugins ship, and how many ship each
- the names of the weftkit plugins that opted into name reporting, see below
- the standard bStats server data (server software and version, Java version, player count,
  core count, operating system, location by country)

Only one plugin per server submits, no matter how many weftkit plugins are installed, so servers
are never counted twice. No player data, plugin configuration, or file contents are collected.
The collected data is publicly visible on the weftkit bStats page.

## Reporting your plugin's name

Plugin names are never reported by default, so the names of private plugins stay off the public
bStats page. If you want your plugin to appear on weftkit's plugins chart, wire a
`MetricsSettings` component that opts in:

```java
@Wired
@Singleton
final class MyMetricsSettings implements MetricsSettings {

    @Override
    public boolean reportName() {
        return true;
    }
}
```

Like any component, the implementation can stay package-private, see
[internal components](internal-components.md).

The setting only affects the name. With or without it, your plugin counts toward the
per-server and version numbers above.

## Opting out as a plugin author

The reporting runs as the bundled `bukkit:metrics` [module](modules.md), so the module levers
apply. Disable it outright on the registry:

```java
@Registry(disable = BukkitModules.METRICS)
public final class HelloPlugin extends WeftPlugin {
    ...
}
```

The module and the bStats reporting are then absent from your generated wiring entirely.

Or decide at runtime through the same `MetricsSettings` component as above, backed by whatever
you like:

```java
@Wired
@Singleton
final class MyMetricsSettings implements MetricsSettings {

    private final JavaPlugin plugin;

    MyMetricsSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enabled() {
        return plugin.getConfig().getBoolean("weftkit-metrics", true);
    }
}
```

Only your plugin stops reporting. Other weftkit plugins on the server and your plugin's own
bStats integration, if it has one, are unaffected. `MetricsSettings` is a
[module settings](modules.md#module-settings) interface: without a wired implementation the
generated defaults apply, metrics on, name off.

To keep the bStats classes out of your jar entirely, exclude them when shading:

```kotlin
tasks.shadowJar {
    exclude("org/bstats/**")
}
```

weftkit detects the missing classes and disables its metrics silently.

## Opting out as a server owner

bStats has a global switch that disables all bStats collection on the server, weftkit's
included: set `enabled: false` in `plugins/bStats/config.yml`.

## Related

- [Modules](modules.md): the mechanism metrics is built on
- [Distribution](distribution.md): what else ships in the plugin jar
- [Getting started](getting-started.md): the dependencies that bring metrics in
