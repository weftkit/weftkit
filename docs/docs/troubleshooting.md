---
title: "Troubleshooting build errors and exceptions"
description: Every build error and runtime exception weftkit can raise, what each one means, and how to fix it, from dependency cycles to stale generated registries.
---

# Troubleshooting

The processor turns wiring mistakes into build errors, so most problems surface during `javac`
with a message pointing at the offending class or parameter. This page lists the errors you are
most likely to meet, what they mean, and how to fix them.

## Build errors

### Dependency must be @Wired or a @Provides product

A constructor parameter declares a type from your own sources that nothing provides. Annotate
the class with `@Wired`, expose a value of that type with `@Provides`, or make the parameter
`Optional<X>` if the dependency is genuinely optional. If the parameter was supposed to carry a
qualifier from a `@Qualified` field, the tag never reached the generated constructor. Register
the annotation as copyable, see [Qualifiers](components.md#qualifiers).

### Component dependency cycle

Two or more components depend on each other, directly or through `@Provides` products and
`@Requires` holders. The message shows the cycle, for example `A -> B -> A`. Break it by
inverting one edge: extract the shared piece into its own component, or have the plugin main
connect the two after startup instead of a constructor parameter.

### WeftLoader is not injectable

A component declared a `WeftLoader` constructor parameter, which 0.6 removed. Inject
[`Weft`](modules.md) to enumerate components by supertype; for anything else, reach the
component from the plugin main, which holds the loader.

### Ambiguous dependency, implemented by ...

An interface or abstract class from your sources has several `@Wired` implementations, so the
processor cannot pick one. Tag the implementations with `@Qualified` and select one at the
injection point, or depend on the concrete class.

### External dependency has multiple @Wired implementations (warning)

Same situation for a type from another module or jar, on a plain component. The dependency
stays unbound because a plain component may receive the value as a `create` argument. Qualify
the implementations, or pass one at the call site. On a singleton the same situation is a
build error, since nothing could ever disambiguate it at runtime.

### Optional dependency of a singleton can never be present (warning)

A singleton's `Optional<X>` parameter has no wired implementation, no product, and no declared
ambient type, and arguments never reach singletons, so it resolves empty on every load. Declare
an ambient type for `X` or drop the parameter.

### Dependency of a singleton must be wired, a product, or a declared ambient type

A singleton (or module) depends on an external type that nothing covers: no `@Wired`
implementation, no `@Provides` product, and no declared [ambient
type](components.md#ambient-roots). Arguments never reach singletons, so this dependency could
never resolve. Declare the type with `@Registry(ambient = X.class)` or a registry constructor
parameter, or wire an implementation.

### Ambient declarations are ambiguous for ...

Two unrelated declared ambient types are assignable to the same constructor parameter, so the
runtime could not pick one. Narrow the parameter type or drop one declaration. Declarations
related by subtyping are fine, they share one value.

### Ambient declaration shadows the @Wired component ...

A type is declared ambient and `@Wired` at once. The ambient layer would always win, so the
wired component could never be injected. Drop one of the two.

### Dependency ... needs explicit create arguments and cannot be injected

A plain component with a parameter only `create` arguments can fill was used as a dependency.
Arguments do not travel into resolved dependencies, so the injection would always fail. Build
the component with `create(X.class, ...)` at the call site, or declare the parameter type as an
ambient type.

### @Wired components need exactly one accessible constructor

The processor needs an unambiguous injection point. Keep one public constructor (package-private
components may use a package-private one) and make any others private, or split the class.

### Stale weftkit registry, run a clean build

The generated registry and a package's `WeftWiring` holder disagree, which only happens
when an incremental build left one of them behind. A clean build regenerates both from the same
sources.

### Static holder is accessed during load without @Requires

A constructor, field initializer, or `load` method reads a `@StaticHolder` class the component
never declared. Add `@Requires(TheHolder.class)` so the component loads after the holder's
initializer.

### No @Wired loader initializes ...

A component `@Requires` a holder that no singleton `@Initializes`. Add `@Initializes` to the
loader that sets the holder up.

### @Registry is already declared / No @Registry class

Exactly one class per plugin carries `@Registry`, usually the plugin main. The registry is
generated into its package.

### Loader implementations must be @Singleton

A plain component is created fresh per injection and would never have its `load` called. Add
`@Singleton`, or drop the `Loader` interface.

### Listeners need at least one @EventHandler method

A `@Wired` `Listener` without handlers would be registered for nothing. Add a handler or remove
the `Listener` interface. Related checks require handlers to be public and to take exactly one
Bukkit event parameter.

## Runtime exceptions

Startup problems the compiler cannot see fail fast during `enable` with a
`ResolutionException` naming the component, or a `ComponentLoadException` wrapping a failed
constructor. weftkit then tears down whatever had already loaded and disables the plugin, so
check the server log for the first exception.

### Cannot resolve dependency ... for ...

A dependency was left to the runtime, usually a declared ambient type without a value or a
`create` parameter without a matching argument. Supply the instance through
`WeftPlugin.ambientValues` (or the extra arguments to `BukkitWeft.enable` or the `WeftLoader`
constructor), or pass it as a call argument.

### Ambient value ... matches no declared ambient type

A value handed to the loader is assignable to none of the declared [ambient
types](components.md#ambient-roots). Declare its type with `@Registry(ambient = X.class)` or a
registry constructor parameter.

### Ambiguous ambient value

Two of the values handed to the loader match the same declared ambient type. The loader rejects
this when it is created, before any component resolves. Pass one value per declared type.

### Arguments never reach a singleton

`create` was called with arguments for a `@Singleton` component. The cached instance is built
from the graph, so the arguments would be silently ignored. Drop them, or make the component
plain if each call should really build a fresh instance.

### Singleton dependency is not loaded

`create` or `get` ran before `load()` or after `unload()`, or reached during startup for a
singleton that loads later.

### Dependency is not available yet

A component resolved during another singleton's `load` hook read a `@Provides` value whose owner
has not loaded yet. Express the ordering with a constructor dependency on the owner, or make the
parameter `Optional<X>`.

### Product ... is null after load

A `@Provides` getter still returned null right after its owner finished loading. Assign the
field inside `load` before returning true.

### Component is not annotated with @Wired

`create` was called with a class the registry does not know. Annotate it with `@Wired` and
rebuild.

## IDE shows WeftWiring as unresolved

`WeftWiring` is generated during compilation, so it does not exist in a fresh checkout
until the first build. Enable annotation processing in your IDE so it regenerates as you edit.
See the note in [Getting started](getting-started.md).


## Related

- [Annotations reference](annotations.md): the contracts behind the build errors
- [Plugin lifecycle](lifecycle.md): rendering the dependency graph for diagnosis
- [Getting started](getting-started.md): a known-good build to compare against
