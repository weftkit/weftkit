# announcer

Rotating server broadcasts. Each broadcast line is its own wired component, and a
[module](https://weftkit.org/modules/) discovers them all, runs the timer, and tears it down
again. This is the example for `@WeftModule`, `Weft.all`, `@ModuleSettings`, and
`@FeatureFlag`.

The packages follow the module split: `broadcast` is the engine, written as a library would ship
it, `announcements` is this plugin's content, and neither knows the other's classes beyond the
`Announcement` interface.

- [`AnnouncerPlugin`](src/main/java/org/weftkit/examples/announcer/AnnouncerPlugin.java) joins
  the module through `@Registry(modules = AnnouncerModule.class)`. Bundled adapter modules need
  no such declaration, plugin-local and third-party ones do.
- [`broadcast/Announcement`](src/main/java/org/weftkit/examples/announcer/broadcast/Announcement.java)
  is the extension point: one method, one broadcast line.
- [`broadcast/AnnouncerModule`](src/main/java/org/weftkit/examples/announcer/broadcast/AnnouncerModule.java)
  implements `ModuleLifecycle`. In `activate` it enumerates every `Announcement` with
  `weft.all`, the one capability constructor injection cannot express, and starts a repeating
  task; `deactivate` cancels it. Adding a broadcast line is adding a class, nothing else changes.
- [`broadcast/AnnouncerModules`](src/main/java/org/weftkit/examples/announcer/broadcast/AnnouncerModules.java)
  holds the module id, so `@Registry(disable = AnnouncerModules.BROADCASTS)` can switch the
  module off without importing it.
- [`broadcast/AnnouncerSettings`](src/main/java/org/weftkit/examples/announcer/broadcast/AnnouncerSettings.java)
  is the module's `@ModuleSettings` interface. No implementation is wired, so the processor
  generates one that keeps the one minute default. Wire a component implementing it to change
  the pace.
- [`announcements/`](src/main/java/org/weftkit/examples/announcer/announcements) holds the
  broadcast lines, each a package-private component the module only ever sees through
  enumeration.
  [`SeasonalAnnouncement`](src/main/java/org/weftkit/examples/announcer/announcements/SeasonalAnnouncement.java)
  is gated by `@FeatureFlag`: while the flag is off the component is never constructed and
  `weft.all` skips it.
- [`flags/ConfigFlags`](src/main/java/org/weftkit/examples/announcer/flags/ConfigFlags.java)
  resolves flags from [`config.yml`](src/main/resources/config.yml), which it writes out on
  first load. Flip `flags.seasonal-broadcast` and restart to watch the component appear and
  disappear. Without such a `FeatureFlags` component, each flag's `enabledByDefault` decides.

## Build

From this directory, using the repository's Gradle wrapper:

```sh
../../../gradlew build
```

The jar lands in `build/libs/announcer-1.0.0.jar`. Drop it into a server's `plugins/` folder and
a broadcast appears every minute, cycling through the wired announcements.

Note that `WeftWiring` is generated during compilation, so a fresh checkout shows it unresolved
in the IDE until the first build. Enable annotation processing in your IDE to regenerate it as
you edit.

## Using this as a template

The example compiles against the weftkit sources in this repository through the
`includeBuild("../../..")` line in [`settings.gradle.kts`](settings.gradle.kts). To start your own
plugin from it, remove that line and set the `org.weftkit` dependencies in
[`build.gradle.kts`](build.gradle.kts) to the latest release.
