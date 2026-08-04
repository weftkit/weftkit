# Contributing

Thank you for your interest in contributing to weftkit.

weftkit grew out of [SilkSpawners](https://github.com/CorneliusMa/SilkSpawners_v2), where the
wiring first shipped inside the plugin before being extracted into this standalone framework.
SilkSpawners remains the largest consumer of weftkit, so it is a good place to see the framework
used in a real plugin - and a good place to validate changes that affect the public API.

## Building

The build uses a Java 17 toolchain and Gradle:

```shell
./gradlew build
```

This compiles all four modules, runs the tests, and produces the jars under
`weftkit-*/build/libs/`. Test coverage reports are written to
`weftkit-*/build/reports/jacoco/`.

The modules are layered: `weftkit-annotations` (the wiring vocabulary) is consumed by
`weftkit-runtime` (the loader) and `weftkit-processor` (compile-time validation and code
generation), and `weftkit-bukkit` adapts everything to the Bukkit plugin lifecycle. Keep
platform-specific code in the adapter. The core modules must stay Bukkit-free (the processor is
extended through the `ComponentRule` SPI instead).

## Tests

Processor behavior is tested with [compile-testing](https://github.com/google/compile-testing)
in `WiredProcessorTest` - every new diagnostic should come with a test that compiles a minimal
source and asserts on the error. Runtime behavior is tested end-to-end in
`WeftLoaderIntegrationTest`, which wires the `Sample` fixture through the real processor at
test-compile time. Prefer extending the fixture over hand-building registries.

## Code style

Java code is formatted with [Palantir Java Format](https://github.com/palantir/palantir-java-format)
enforced through [Spotless](https://github.com/diffplug/spotless). The build fails on unformatted
code, so format your changes with

```shell
./gradlew spotlessApply
```

before committing - or enable the git hooks below to have this happen automatically.

## Git hooks

The repository ships optional git hooks in `.githooks/` that format staged Java files with
Spotless before each commit and validate commit messages against the Conventional Commits format.
Enable them once per clone with:

```shell
git config core.hooksPath .githooks
```

## Commits

Commit messages follow the [Conventional Commits](https://www.conventionalcommits.org)
specification, enforced by the `commit-msg` hook and CI:

```
<type>[optional scope]: <description>
# OR, FOR BREAKING CHANGES
<type>[optional scope]!: <description>
```

Where `type` is one of `build`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `style`,
`test`, `revert`, `chore`. Useful scopes are the module short names: `runtime`, `processor`,
`bukkit`, `annotations`.

Example: `fix(runtime): tear down singletons on aborted load`

## Releases

Releases are cut by pushing a `v*` tag: the release workflow builds, signs, and publishes the
tagged version to Maven Central (`v1.2.3` releases `1.2.3`). weftkit is pre-1.0, so the public
API may still change between minor releases.
