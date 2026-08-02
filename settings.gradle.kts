rootProject.name = "weftkit"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

include(
    "weftkit-annotations",
    "weftkit-runtime",
    "weftkit-processor",
    "weftkit-bukkit",
)
