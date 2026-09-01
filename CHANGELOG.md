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
- The rendered dependency graph marks modules and feature-flagged components, and draws ambient
  roots as input nodes with the edges into them.
- `WeftLoader.ambient(type)` and `WeftLoader.registry()` accessors.
- `WeftLoader` is confined to its creating thread: any access touching mutable loader state,
  reads like `get` and the load timings included, fails fast from another thread with a clear
  exception instead of silently racing the graph. Views of the immutable registry stay
  accessible from any thread, and `loadTimings` returns a snapshot instead of a live view.
- A lazy singleton whose materialization failed stays failed: further injections rethrow the
  first failure cheaply until `WeftLoader.resetFailure(type)` or an unload clears the marker.
- `ComponentRegistry.featureFlags()` records each `@FeatureFlag` in the generated registry, so
  the runtime gates components without any annotation lookup.
- Declared ambient types: the registry class, its constructor parameters, and the new
  `@Registry(ambient = ...)` attribute name every type an ambient value may carry, recorded in
  the generated registry as `ComponentRegistry.ambientTypes()`. The processor rejects a
  singleton dependency no layer covers and ambient declarations that are ambiguous for a
  parameter, so both error classes fail the build instead of the server. A singleton's
  optional dependency nothing can ever fill is flagged as a warning.
- The loader validates ambient values on construction: a value matching no declared type and a
  second value for one declared type are rejected before any component resolves. Injection
  matches through the declared types, never through the value's runtime type.
- A declared ambient type with a single wired implementation keeps the binding as a fallback,
  so an ambient value overrides the implementation and its absence changes nothing. This is the
  seam for shadowing a wired dependency with a test fake.
- `ModuleContributor.ambientTypes()` lets adapters declare ambient types their runtime supplies.
  The Bukkit adapter declares `JavaPlugin`.
- A plain component whose parameter only explicit `create` arguments can fill is rejected as a
  dependency of other components at compile time.

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
- `ComponentLoadException` and `ResolutionException` moved to
  `org.weftkit.wiring.runtime.exception`.
- `create` and `createAll` arguments resolve deterministically: among several assignable
  arguments the first given wins, replacing the runtime ambiguity exception. `create` with
  arguments on a singleton throws immediately instead of silently ignoring them.

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
