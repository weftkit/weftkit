# Changelog

Notable changes to weftkit. The format follows [Keep a Changelog](https://keepachangelog.com/).

## 0.6.0 - Unreleased

### Added

- Modules: `@WeftModule` classes woven into the consuming graph at compile time as eager
  singletons, with `ModuleLifecycle` hooks running after load and in reverse before unload.
  Declared with `@Registry(modules = ...)`, disabled by id with `@Registry(disable = ...)`.
- `@ModuleSettings` interfaces for consumer configuration of a module, backed by a generated
  all-defaults implementation when no implementation is wired.
- `@FeatureFlag` gates component construction behind a named flag, resolved through an ambient
  or wired `FeatureFlags` implementation. Nothing may require a flagged component, enforced at
  compile time. A consumer that tolerates its absence depends on it as `Optional<T>`, which
  resolves empty while the flag is off. This lets a config-gated subsystem reach its consumers.
- `Weft`, the injectable enumeration facade: `all(supertype)` returns every enabled component.
- `ModuleContributor` SPI for adapters to contribute modules to every generated registry.
- Command binding: `@CommandHandler` executors bind to their `plugin.yml` commands through the
  bundled `bukkit:commands` module, with the handler contract checked at compile time.
- Plugin metrics: `MetricsSettings.serviceId()` plus `MetricsContributor` and `PluginMetrics`
  publish your plugin's own bStats charts through the bundled `bukkit:metrics` module.
- Runnable example plugins under `examples/`, built in CI.
- The rendered dependency graph marks modules and feature-flagged components.
- `WeftLoader.ambient(type)` and `WeftLoader.registry()` accessors.
- `WeftLoader` is confined to its creating thread: any access touching mutable loader state,
  reads like `get` and the load timings included, fails fast from another thread with a clear
  exception instead of silently racing the graph. Views of the immutable registry stay
  accessible from any thread, and `loadTimings` returns a snapshot instead of a live view.
- `ComponentRegistry.featureFlags()` records each `@FeatureFlag` in the generated registry, so
  the runtime gates components without any annotation lookup.

### Changed

- Listener registration and weftkit's bStats reporting run as the bundled modules
  `bukkit:listeners` and `bukkit:metrics`, disable them via `BukkitModules`.
- Metrics configuration moved from annotations on the plugin main to a wired `MetricsSettings`
  component, so it can be decided at runtime.
- The Bukkit adapter's compile-time side ships as the new `weftkit-bukkit-processor` artifact:
  declare it on `annotationProcessor` instead of `weftkit-bukkit`. The listener validation rule
  moved there, and plugin jars stop shipping processor service files.
- `ComponentRegistry`, `Dependency`, and `Registries` moved to
  `org.weftkit.wiring.runtime.registry`.

### Removed

- `@WeftMetrics` and `@NoMetrics`, replaced by `MetricsSettings` and
  `@Registry(disable = BukkitModules.METRICS)`.
- The bundled bStats dependency. All metrics, weftkit's own reporting included, only run when
  the consuming plugin adds bStats itself, so no plugin carries telemetry without its build
  saying so.
- `WeftLoader` self-injection. A `WeftLoader` constructor parameter now fails the build; inject
  `Weft` to enumerate components, and reach everything else from the plugin main.
- `BukkitWeft.registerListeners`, superseded by the bundled listener module.
- Published Gradle module metadata.
