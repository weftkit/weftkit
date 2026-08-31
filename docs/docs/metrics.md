---
title: "Metrics: plugin charts and what bStats collects"
description: weftkit ships no telemetry by default. Add bStats to publish your plugin's own charts; weftkit's anonymous usage reporting rides along, and this page lists exactly what it collects.
---

# Metrics

weftkit integrates with [bStats](https://bstats.org), the standard metrics service for Bukkit
plugins, but does not bundle it: a jar built without bStats contains no metrics code and
transmits nothing. Adding bStats to your build turns two things on:

- your plugin's own bStats page, fed through the metrics API below
- weftkit's anonymous usage reporting, which rides along and shows which weftkit versions are
  actually in use, guiding deprecations and compatibility decisions

That trade is the deal, stated up front: shipping bStats in your jar enables both, never one
without your build saying so.

## Enabling metrics

Add bStats next to weftkit and relocate it when shading:

```kotlin
dependencies {
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

tasks.shadowJar {
    relocate("org.bstats", "com.example.myplugin.lib.bstats")
}
```

Without shading, list it under `libraries` in `plugin.yml` like weftkit itself, see
[distribution](distribution.md).

## Your plugin's charts

[Register your plugin on bStats](https://bstats.org) to get a service id, and wire a
`MetricsSettings` component that returns it:

```java
@Wired
@Singleton
final class MyMetricsSettings implements MetricsSettings {

    @Override
    public int serviceId() {
        return 12345;
    }
}
```

Any wired component can then implement `MetricsContributor` to publish charts:

```java
@Wired
@Singleton
final class HomeStore implements MetricsContributor {

    @Override
    public void contribute(PluginMetrics metrics) {
        metrics.add(new SimplePie("storage_backend", () -> "yaml"));
    }
}
```

`PluginMetrics.add` takes the standard bStats chart types (`SimplePie`, `AdvancedPie`,
`SingleLineChart`, ...), which pull from their suppliers on every submission, so registering
once is the whole lifecycle. Contributors are collected once the graph is up, feature flagged
ones excluded, and only called when metrics actually run, so they never check availability
themselves. A contributor that throws is logged and skipped without affecting the others.

## What weftkit reports

On a server running metrics-enabled weftkit plugins, the first such plugin to enable submits,
once per bStats interval:

- how many plugins on the server ship weftkit
- which weftkit versions those plugins ship, and how many ship each
- the names of the weftkit plugins that opted into name reporting, see below
- the standard bStats server data (server software and version, Java version, player count,
  core count, operating system, location by country)

Only one plugin per server submits, no matter how many weftkit plugins are installed, so servers
are never counted twice. No player data, plugin configuration, or file contents are collected.
The collected data is publicly visible on the weftkit bStats page.

Plugins that ship weftkit without bStats never transmit anything themselves, but a submitter on
the same server still counts them anonymously: weftkit plugins are recognized by the
`weftkit-version.properties` resource in their jar, which stays after relocation. To keep your
plugin out of even those counts, exclude the resource when shading:

```kotlin
tasks.shadowJar {
    exclude("weftkit-version.properties")
}
```

## Reporting your plugin's name

Plugin names are never reported by default, so the names of private plugins stay off the public
bStats page. If you want your plugin to appear on weftkit's plugins chart, opt in on the same
`MetricsSettings` component:

```java
@Override
public boolean reportName() {
    return true;
}
```

Like any component, the implementation can stay package-private, see
[internal components](internal-components.md). The setting only affects the name; with or
without it, your plugin counts toward the per-server and version numbers above.

## Opting out of the ride-along

To publish your own charts without weftkit's reporting, or to make it configurable at runtime,
decide through `MetricsSettings`:

```java
@Override
public boolean enabled() {
    return plugin.getConfig().getBoolean("weftkit-metrics", true);
}
```

Or drop the whole `bukkit:metrics` [module](modules.md), your charts included, from the
generated wiring:

```java
@Registry(disable = BukkitModules.METRICS)
public final class HelloPlugin extends WeftPlugin {
    ...
}
```

`MetricsSettings` is a [module settings](modules.md#module-settings) interface: without a wired
implementation the generated defaults apply, no service id, name off, ride-along on when bStats
ships.

## Opting out as a server owner

bStats has a global switch that disables all bStats collection on the server, weftkit's
included: set `enabled: false` in `plugins/bStats/config.yml`.

## Related

- [Modules](modules.md): the mechanism metrics is built on
- [Distribution](distribution.md): shading and relocating what ships in the plugin jar
- [Feature flags](modules.md#feature-flags): gating contributors like any component
