---
description: weftkit wires package-private classes, so a plugin's internals stay internal while still being injected, tested, and lifecycle-managed.
---

# Internal components

Most dependency injection frameworks quietly force every component to be `public`, because the
generated wiring lives in one package and must reach all of them. That turns internals into API:
helper classes that were deliberately package-private have to be opened up just to become
[injectable](components.md).

weftkit wires package-private classes. A component may be package-private, and so may its
constructor, its constructor parameter types, and its `@Provides` product types, as long as the
non-public types live in the component's own package.

## The facade pattern

The typical shape is a public facade with hidden collaborators:

```java title="locale/LocaleHandler.java"
@Wired
@Singleton
public final class LocaleHandler {

    private final LocaleFiles files;

    public LocaleHandler(LocaleFiles files) {
        this.files = files;
    }
}
```

```java title="locale/LocaleFiles.java"
@Wired
@Singleton
final class LocaleFiles {

    LocaleFiles(HelloPlugin plugin) { ... }
}
```

`LocaleFiles` stays invisible outside its package, yet it is constructor-injected, singleton
scoped, and torn down with everything else. Because your [tests](testing.md) live in the same
package, they
can construct it directly or replace it, which is the main practical win over `new` inside the
facade.

## What gets generated

The central `WeftWiring` registry cannot name types it cannot access, so for every package that
contains non-public wiring the processor generates a small `WeftWiring` holder inside that
package. It carries the factories and metadata for that package's hidden components, and the
central registry merges the holders at class initialization. The holders are implementation
detail: they are marked as internal, offer no compatibility guarantees, and expose nothing
beyond what the wiring already needs.

Package-private components behave like public ones everywhere else. They participate in load
order, lifecycle hooks, `createAll` discovery, and interface bindings, and they render with
dashed borders in the generated `weftkit-graph.dot` so the public surface of your plugin is
visible at a glance.

## Rules

- A `@Wired` class must be public or package-private, top-level or static nested. Private and
  protected nested classes stay rejected.
- A package-private component needs exactly one non-private constructor. Public components keep
  the exactly-one-public-constructor rule. Prefer a package-private constructor on a
  package-private class: a `public` one is capped by the class visibility anyway and only
  misstates the intent (with Lombok, use
  `@RequiredArgsConstructor(access = AccessLevel.PACKAGE)`).
- Constructor parameter types and product types must be public or live in the component's own
  package.

If an incremental build ever leaves the central registry and a package holder out of sync, the
plugin fails fast at class initialization with `Stale weftkit registry, run a clean build`.
