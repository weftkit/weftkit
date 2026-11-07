# Changelog

Notable changes to weftkit. The format follows [Keep a Changelog](https://keepachangelog.com/).

## 0.6.0 - Unreleased

### Added

- Directly injectable `TaskScope`: each consuming singleton gets its own tasks, cancelled before
  unload or on failed construction or startup. Manual scopes use `scheduler.scope()`.
- `@Provides(owned = true)` factories supply an `AutoCloseable` per consuming singleton,
  automatically closed with that consumer. Shared `@Provides` behavior is unchanged.
- Injectable `WeftScope` registers `AutoCloseable` resources for cleanup in reverse order.
  Eager and lazy singletons are supported; prototypes and qualified scopes fail compilation.
- Injectable `WeftDiagnostics` with immutable snapshots of graph and module phases, component
  scopes and states, observed feature flags, singleton timings, and sticky lazy failures.
  Inspection never constructs components or evaluates flags. Optional `DiagnosticsCommand`
  exposes filtered, permission-checked Bukkit command output for a plugin's own graph.

- Modules: `@WeftModule` classes woven into the consuming graph at compile time as eager
  singletons loading before every other component, with `ModuleLifecycle` hooks running after
  load and in reverse before unload.
  Declared with `@Registry(modules = ...)`, disabled by id with `@Registry(disable = ...)`.
- `@ModuleSettings` interfaces for consumer configuration of a module, backed by a generated
  all-defaults implementation when no implementation is wired.
- `@FeatureFlag` gates component construction behind a named flag, resolved through an ambient
  or wired `FeatureFlags` implementation. Nothing may require a flagged component, enforced at
  compile time. A consumer that tolerates its absence depends on it as `Optional<T>`, which
  resolves empty while the flag is off. This lets a config-gated subsystem reach its consumers.
- `Weft`, the injectable enumeration facade: `all(supertype)` returns every enabled component,
  and `annotated(supertype, annotation)` only the annotated ones, each paired with its
  annotation, leaving the rest unbuilt.
- `@HandledBy` names the module that reads a component annotation. The processor records such
  annotations in the generated registry, so modules read them without reflection. Adapters warn
  at enable when that module is missing from the registry, and the build fails when a component
  carries an annotation of a module the registry disables.
- `ModuleContributor` SPI for adapters to contribute modules to every generated registry.
- Command binding: `@CommandHandler` executors bind to their `plugin.yml` commands through the
  bundled `bukkit:commands` module, with the handler contract checked at compile time.
- Paper adapter: `weftkit-paper` and `weftkit-paper-processor`, built on the Bukkit adapter for
  Paper 1.21.1 or newer. `@PaperCommand` registers a wired `BasicCommand` or `CommandTree` with
  Paper's Brigadier registrar through the bundled `paper:commands` module, no `plugin.yml` entry
  needed, so `paper-plugin.yml` plugins work too. Disabling hides the commands, since Paper
  cannot unregister them.
- Plugin metrics: `MetricsSettings.serviceId()` plus `MetricsContributor` and `PluginMetrics`
  publish your plugin's own bStats charts through the bundled `bukkit:metrics` module.
- `Scheduler`, an ambient type injectable into every component, shaped after Folia's threaded
  regions. `global()`, `at(Location)`, and `on(Entity)` return a `Region` with `run`,
  `runLater`, `runRepeating`, and `isCurrentThread`. `async()` runs off the server threads.
  Every scheduling call returns a `Task` handle. On Bukkit and Paper every region is the main
  thread. The bundled `bukkit:scheduler` module cancels every task scheduled through it as the
  graph unloads, after every other unload hook, so an aborted enable leaves none running. The
  default Bukkit scheduler refuses to enable on Folia.
  A `@Provides Scheduler` product replaces it, and `WeftPlugin.scheduler()` or the
  `BukkitWeft.enable` overload forces one.
- `weftkit-folia`: a `Scheduler` over Folia's region, entity, and async schedulers, for Bukkit
  and Paper plugins alike, on Folia 1.20.1 or newer. With it on the compile classpath, the
  Bukkit processor weaves the `folia:scheduler` module into the registry. Its
  `SchedulerDetection` provides Folia's regions on Folia and the Bukkit scheduler elsewhere.
  Disable with `FoliaModules.SCHEDULER`.
- Runnable example plugins under `examples/`, built in CI.
- The rendered dependency graph marks modules and feature-flagged components, and draws ambient
  roots as input nodes with the edges into them. Dotted edges link each module to the components
  it handles through a `@HandledBy` annotation, and a component whose module is missing is
  marked inert.
- `WeftLoader.ambient(type)` and `WeftLoader.registry()` accessors.
- `WeftLoader` threading contract: `load` stays on the loader's creating thread, and every
  other call fails fast from another thread while the graph loads or stands unloaded. Once
  loaded, any thread may call `get`, `create`, `Weft.all`, the view methods, the timings,
  `resetFailure`, and `unload`. Reads are lock-free, creation and teardown serialize on an
  internal lock, so a lazy singleton materializes once, its `load` hook running on the thread
  that demanded it first. Views of the immutable registry work from any thread, and
  `loadTimings` returns a snapshot.
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

- `WeftView` focuses on graph queries; use `WeftDiagnostics` for timings. The loader's
  `loadTimings()` and `totalLoadTime()` methods remain available for manual integrations.
- Generated registry collections specify their type arguments explicitly to avoid expensive
  javac inference on large graphs. Product capture indexes products by owner instead of scanning
  every provider for each component.
- `WeftLoader.load` throws when the graph is already loaded instead of running the eager loop
  over it again, and `unload` clears the load timings.
- Listener registration and weftkit's bStats reporting run as the bundled modules
  `bukkit:listeners` and `bukkit:metrics`, disable them via `BukkitModules`.
- Metrics configuration moved from annotations on the plugin main to a wired `MetricsSettings`
  component, so it can be decided at runtime.
- `MetricsModule` takes the `Scheduler` as its second constructor argument and schedules its
  claim through it.
- `BukkitWeft.enable` gained an overload taking a `Scheduler`, and `BukkitWeft.scheduler`
  returns the one a loader runs with. A null extra ambient array means no extra values. Startup
  failures while constructing the loader are now logged and disable the plugin like any other
  startup failure instead of escaping `onEnable`.
- The Bukkit adapter's compile-time side ships as the new `weftkit-bukkit-processor` artifact:
  declare it on `annotationProcessor` instead of `weftkit-bukkit`. The listener validation rule
  moved there, and plugin jars stop shipping processor service files.
- The `weftkit-annotations` module is renamed `weftkit-api` and now holds the whole compile-time
  API: the annotations, the `Loader` and `ModuleLifecycle` lifecycle interfaces, the `FeatureFlags`
  hook (moved from the runtime to `org.weftkit.wiring`), and the generated registry contract. The
  processor depends on `weftkit-api` alone, no longer on the runtime.
- The registry contract moved to `org.weftkit.wiring.registry` in `weftkit-api`.
- `ComponentLoadException` and `ResolutionException` moved to
  `org.weftkit.wiring.runtime.exception`.
- `create` and `createAll` arguments resolve deterministically: among several assignable
  arguments the first given wins, replacing the runtime ambiguity exception. `create` with
  arguments on a singleton throws immediately instead of silently ignoring them.

### Fixed

- Invalid wiring stops registry and graph generation, including errors from adapter rules and
  static-holder checks. Warnings still allow generation.
- Module activation runs once per loaded graph; repeated activation while active and repeated
  deactivation do not repeat hooks. Unload deactivates still-active modules, and retry after a
  failed attempt requires unload/load.
- Cleanup continues when hooks throw the same exception instance, without attempting exception
  self-suppression.
- Unchecked errors during component construction, load, module activation, and teardown trigger
  best-effort cleanup. Original startup errors propagate with rollback failures suppressed.
- Built-in Bukkit and Folia schedulers close permanently during graph teardown, reject new work,
  skip pending callbacks, and cancel handles returned by in-flight scheduling calls. `cancelAll`
  stays reusable; custom schedulers can override the new `Scheduler.close` lifecycle hook.
- The owner thread cannot start new graph operations while another thread unloads the graph.
  Already-admitted cache reads and prototype creation may finish without delaying teardown.
- Plugin hook failures always tear down the graph; registry and ambient hook failures disable
  the plugin through the same startup failure handling.
- Failed module activation rolls back the module's partial registrations as well as earlier modules.
- Task cancellation attempts every handle even when a platform cancellation throws.
- Reentrant unload during loading, singleton creation, or teardown is rejected.
- A constructed singleton whose load hook throws or returns false receives its unload hook.
- Wiring types generated after registry generation fail compilation with an actionable diagnostic.

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
