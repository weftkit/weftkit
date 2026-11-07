---
title: Diagnostics
---

# Diagnostics

Inject `WeftDiagnostics` to inspect your plugin's graph. Snapshots are immutable and never
construct components or evaluate feature flags.

## Plugin API

```java
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.runtime.WeftDiagnostics;

@Wired
final class HealthReporter {
    private final WeftDiagnostics diagnostics;

    HealthReporter(WeftDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    WeftDiagnostics.Snapshot snapshot() {
        return diagnostics.snapshot();
    }
}
```

No module or registration is required. Manual integrations use `loader.diagnostics()`.

| Snapshot field | Contents |
| --- | --- |
| `state()` | Graph lifecycle phase. |
| `moduleState()` | Module activation phase, separate from component loading. |
| `components()` | All registered components, sorted by class name. Includes scope, state, module membership, flag, observed enabled decision, timing, and failure. |
| `loadOrder()` | Declared eager singleton order, including flagged components skipped during load. |
| `totalLoadTime()` | Sum of successful singleton timings. Nested lazy loads can be counted twice. |

- `enabled()` is true for unflagged components, the last checked decision for flagged ones,
  or empty if never checked. It does not indicate whether an instance is loaded.
- `failure()` describes a retained lazy failure, cleared by `resetFailure` or unload.
  Exception messages may be null. Startup and teardown errors are not retained here.
- Prototype instances are not tracked. Unload clears timings and flag observations.
- Capture follows the [loader's threading contract](lifecycle.md) and waits for singleton
  creation or teardown. Concurrent flag observations can change independently. Returned
  snapshots can be read from any thread.

Use `WeftView` for live membership and enabled-type queries; use diagnostics for timings and state snapshots.

## Bukkit command

Bind the executor with a wired subclass:

```java
@Wired
@Singleton
@CommandHandler("myplugindebug")
final class PluginDiagnostics extends DiagnosticsCommand {
    PluginDiagnostics(WeftDiagnostics diagnostics) {
        super(diagnostics);
    }
}
```

Import `DiagnosticsCommand` from `org.weftkit.wiring.bukkit.diagnostics` and `CommandHandler`
from `org.weftkit.wiring.bukkit.commands`. Keep the `bukkit:commands` module enabled and add
to your existing `plugin.yml`:

```yaml
commands:
  myplugindebug:
    permission: weftkit.diagnostics
permissions:
  weftkit.diagnostics:
    default: op
```

`/myplugindebug` shows a summary. List subcommands are `components`, `modules`, `flags`,
`failures`, and `timings`. Append a class-name filter, such as `/myplugindebug components storage`.
Lists show up to 50 rows; failure output omits exception messages.

Manual Bukkit integrations can register `new DiagnosticsCommand(diagnostics)` as an executor.
Paper plugins using `paper-plugin.yml` need their own Paper command bridge.

See [Announcer](examples/announcer.md) for a complete example.
