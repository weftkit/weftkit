description = "The weftkit Bukkit adapter: plugin lifecycle entry points and listener wiring"

dependencies {
    api(project(":weftkit-runtime"))
    // Consumers add bStats themselves
    compileOnly(libs.bstats)
    compileOnly(libs.bukkit)

    testImplementation(libs.bukkit)
    testImplementation(libs.bstats)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Stamps the build version into the resource WeftkitMetrics reports to bStats
tasks.named<Copy>("processResources") {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("weftkit-version.properties") {
        expand("version" to version)
    }
}
