description = "The weftkit Folia support: a Scheduler over the threaded regions API, for Bukkit and Paper plugins alike"

dependencies {
    api(project(":weftkit-bukkit"))
    compileOnly(libs.folia)

    testImplementation(libs.folia)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
