---
description: Every weftkit build error and runtime exception, what causes it, and how to fix it.
---

# Troubleshooting

The processor turns wiring mistakes into build errors, so most problems surface during `javac`
with a message pointing at the offending class or parameter. This page lists the errors you are
most likely to meet, what they mean, and how to fix them.

## Build errors

### Dependency must be @Wired or a @Provides product

A constructor parameter declares a type from your own sources that nothing provides. Annotate
the class with `@Wired`, expose a value of that type with `@Provides`, or make the parameter
`Optional<X>` if the dependency is genuinely optional.

### Component dependency cycle

Two or more components depend on each other, directly or through `@Provides` products and
`@Requires` holders. The message shows the cycle, for example `A -> B -> A`. Break it by
inverting one edge: extract the shared piece into its own component, or have one side reach the
other at runtime through an injected `WeftLoader` instead of a constructor parameter.

### Ambiguous dependency, implemented by ...

An interface or abstract class from your sources has several `@Wired` implementations, so the
processor cannot pick one. Tag the implementations with `@Qualified` and select one at the
injection point, or depend on the concrete class.

### External dependency has multiple @Wired implementations (warning)

Same situation for a type from another module or jar. The dependency stays unbound because such
a value may legitimately arrive as an ambient root. Either qualify the implementations or pass
one instance to `BukkitWeft.enable` as an extra ambient value.

### @Wired components need exactly one public constructor

The processor needs an unambiguous injection point. Keep one public constructor and make any
others private, or split the class.

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

Startup problems the compiler cannot see fail fast during `enable` with an
`IllegalStateException` or `ComponentLoadException`. weftkit then tears down whatever had
already loaded and disables the plugin, so check the server log for the first exception.

### Cannot resolve dependency ... for ...

A dependency on an external type was left unbound at compile time and nothing supplied it at
runtime. Pass an instance to `BukkitWeft.enable` as an extra ambient value.

### Ambiguous argument / Ambiguous ambient value

Two of the values you passed, either to `create`/`createAll` or as ambient roots, are assignable
to the same constructor parameter. Pass a single unambiguous value or use a qualified binding.

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

## IDE shows WiredComponents as unresolved

`WiredComponents` is generated during compilation, so it does not exist in a fresh checkout
until the first build. Enable annotation processing in your IDE so it regenerates as you edit.
See the note in [Getting started](getting-started.md).
