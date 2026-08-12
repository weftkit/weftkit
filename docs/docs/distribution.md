---
description: How to package a weftkit plugin - shading and relocation, the libraries alternative, and why plugins with an API should publish it as a separate artifact.
---

# Distribution

A Bukkit server loads a plugin from a single jar and resolves no dependencies for it, so a
weftkit plugin ships as a fat jar: your classes plus weftkit's, bundled by a tool like
[Shadow](https://gradleup.com/shadow/). Relocate weftkit while shading, like any bundled
library, so several plugins can carry different weftkit versions without interfering:

```kotlin
tasks.shadowJar {
    relocate("org.weftkit", "com.example.myplugin.lib.org.weftkit")
}
```

weftkit's bStats integration rides along, relocated by an `org.bstats` rule if your plugin
already has one and working unrelocated otherwise. Opting out of the metrics entirely is
covered on the [metrics](metrics.md) page.

## The libraries alternative

Plugins with a compatibility floor of 1.16.5 or newer can skip shading and declare weftkit in
`plugin.yml` instead. The server downloads it on first start:

```yaml
libraries:
  - org.weftkit:weftkit-bukkit:0.3.0
```

- No weftkit classes in your jar, no relocation.
- Every plugin resolves its own copy through its own classloader.
- Needs an internet connection on the server's first start.

## Exposing your own API

If other plugins hook into yours, publish the API as its own thin artifact and let them compile
against that, never against the plugin jar. The fat jar is a deployment artifact: everything
public inside it, including the relocated weftkit and the generated wiring, lands in the
dependent developer's classpath and autocomplete, where a relocated `WeftLoader` sits right next
to the real one. A thin API module contains exactly the types a hook needs and nothing else.

The clean shape is an interface in the API artifact, implemented by a wired component in the
plugin and registered with Bukkit's `ServicesManager` during load, so dependents reach the live
instance through the interface alone. Your API service can stay package-private, see
[internal components](internal-components.md).
