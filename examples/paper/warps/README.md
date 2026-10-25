# warps

Named warps: `/warp set <name>` saves where you stand, `/warp <name>` teleports you there, and
`/warps` lists them. Shows Paper's Brigadier commands wired by weftkit: a `CommandTree` with
subcommands, argument suggestions, and requirements, next to a `BasicCommand`. Both are
`@PaperCommand` components, so `paper-plugin.yml` declares no commands.

## Build

From this directory, using the repository's Gradle wrapper:

```sh
../../../gradlew build
```

The jar lands in `build/libs/warps-1.0.0.jar`. Needs Paper 1.21.1 or newer. To use this example
as a template for your own plugin, see the [examples README](../../README.md).
