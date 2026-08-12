---
description: weftkit reports anonymous usage numbers to bStats. This page lists exactly what is collected and how plugin authors and server owners opt out.
---

# Metrics

weftkit collects anonymous usage statistics through [bStats](https://bstats.org), the standard
metrics service for Bukkit plugins. The numbers show which weftkit versions are actually in use
and how many plugins run on them, which guides deprecations and compatibility decisions.

## What is collected

On a server running weftkit-based plugins, the first such plugin to enable submits, once per
bStats interval:

- the names of the plugins on the server that ship weftkit, and how many there are
- the weftkit version each of those plugins ships
- the standard bStats server data (server software and version, Java version, player count,
  core count, operating system, location by country)

Only one plugin per server submits, no matter how many weftkit plugins are installed, so servers
are never counted twice. No player data, plugin configuration, or file contents are collected.
The collected data is publicly visible on the weftkit bStats page.

## Opting out as a plugin author

Annotate your plugin main with `@NoMetrics`:

```java
@Registry
@NoMetrics
public final class HelloPlugin extends WeftPlugin {
    ...
}
```

Only your plugin stops reporting. Other weftkit plugins on the server and your plugin's own
bStats integration, if it has one, are unaffected.

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
