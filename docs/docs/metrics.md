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
bStats page. If you want your plugin to appear on weftkit's plugins chart, annotate your plugin
main with `@WeftMetrics(reportName = true)`:

```java
@Registry
@WeftMetrics(reportName = true)
public final class HelloPlugin extends WeftPlugin {
    ...
}
```

The setting only affects the name. With or without it, your plugin counts toward the
per-server and version numbers above.

## Opting out as a plugin author

Annotate your plugin main with `@WeftMetrics(enabled = false)`:

```java
@Registry
@WeftMetrics(enabled = false)
public final class HelloPlugin extends WeftPlugin {
    ...
}
```

Only your plugin stops reporting. Other weftkit plugins on the server and your plugin's own
bStats integration, if it has one, are unaffected. The `@NoMetrics` annotation from 0.3 still
works but is deprecated in favor of `@WeftMetrics(enabled = false)`.

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
