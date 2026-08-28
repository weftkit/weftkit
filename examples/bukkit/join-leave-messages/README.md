# join-leave-messages

The smallest useful weftkit plugin: it replaces the default join and leave broadcasts with its
own messages. Three classes show the whole wiring model:

- [`JoinLeavePlugin`](src/main/java/org/weftkit/examples/joinleave/JoinLeavePlugin.java) is the
  plugin main. It carries `@Registry`, extends `WeftPlugin`, and points at the generated
  `WeftWiring`. Nothing else.
- [`messages/Messages`](src/main/java/org/weftkit/examples/joinleave/messages/Messages.java) is
  a plain `@Wired @Singleton` component that formats the broadcasts. It is public because it is
  injected across packages.
- [`listeners/ConnectionListener`](src/main/java/org/weftkit/examples/joinleave/listeners/ConnectionListener.java)
  gets `Messages` injected through its constructor and handles `PlayerJoinEvent` and
  `PlayerQuitEvent`. weftkit registers it automatically so there is no `registerEvents` call
  anywhere. It stays package-private even in its own package: weftkit generates a wiring holder
  per package with non-public components, see
  [internal components](https://weftkit.org/internal-components/).

The layout is the usual feature-package shape: the plugin main and the generated `WeftWiring`
at the root, everything else grouped by concern.

The build follows the [distribution guide](https://weftkit.org/distribution/): Shadow bundles
weftkit into the plugin jar and relocates it, so several weftkit plugins can coexist on one
server.

## Build

From this directory, using the repository's Gradle wrapper:

```sh
../../../gradlew build
```

The jar lands in `build/libs/join-leave-messages-1.0.0.jar`. Drop it into a server's `plugins/`
folder, join, and the custom message appears.

Note that `WeftWiring` is generated during compilation, so a fresh checkout shows it unresolved
in the IDE until the first build. Enable annotation processing in your IDE to regenerate it as
you edit.

## Using this as a template

The example compiles against the weftkit sources in this repository through the
`includeBuild("../../..")` line in [`settings.gradle.kts`](settings.gradle.kts). To start your own
plugin from it, remove that line and set the `org.weftkit` dependencies in
[`build.gradle.kts`](build.gradle.kts) to the latest release.
