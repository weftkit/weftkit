rootProject.name = "weftkit"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include(
    "weftkit-api",
    "weftkit-runtime",
    "weftkit-processor",
    "weftkit-bukkit",
    "weftkit-bukkit-processor",
    "weftkit-paper",
    "weftkit-paper-processor",
)

includeBuild("examples/bukkit/join-leave-messages")
includeBuild("examples/bukkit/player-homes")
includeBuild("examples/bukkit/announcer")
includeBuild("examples/paper/warps")
