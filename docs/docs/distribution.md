---
title: "Shading and distributing a Bukkit plugin jar"
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

weftkit bundles no third-party libraries, bStats included: the jar carries metrics code only if
your build adds bStats, covered on the [metrics](metrics.md) page.

Shadow's `minimize()` is safe to use. weftkit [modules](modules.md) and their components are
ordinary code in your generated wiring: the bundled ones (listener wiring, weftkit's metrics) are
contributed by the adapter at compile time, and third-party ones are declared on your registry,
`@Registry(modules = SomeModule.class)`, next to them. Nothing is discovered by runtime classpath
scanning, so neither minimization nor relocation can strip or hide a module.

weftkit is [Apache-2.0](https://github.com/weftkit/weftkit/blob/main/LICENSE), so a fat jar that
bundles it must ship weftkit's license, and any other bundled library's alongside it. bStats,
if you add it for [metrics](metrics.md), brings its MIT license. These are separate from your
plugin's own license, so give each its own path rather than a shared `META-INF/LICENSE`, where
one would overwrite another. Keep the third-party licenses under `META-INF/licenses/`:

```kotlin
tasks.shadowJar {
    from("licenses") { into("META-INF/licenses") }
}
```

Here `licenses/` holds a copy of each bundled dependency's license. Build the jar and confirm they
survived, since `minimize()` and merge rules can drop loose files.

## The libraries alternative

Plugins with a compatibility floor of 1.16.5 or newer can skip shading and declare weftkit in
`plugin.yml` instead. The server downloads it on first start:

```yaml
libraries:
  - org.weftkit:weftkit-bukkit:{{ weftkit_version }}
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

## Related

- [Getting started](getting-started.md): the compile-time side of the build
- [Modules](modules.md): publishing a module for other plugins to use
- [Metrics](metrics.md): the bStats dependency that turns metrics on
- [Examples overview](examples/index.md): complete builds that shade and relocate weftkit
