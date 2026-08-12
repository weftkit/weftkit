description = "The weftkit Bukkit adapter: plugin lifecycle entry points and listener wiring"

dependencies {
    api(project(":weftkit-runtime"))
    implementation(libs.bstats)
    compileOnly(project(":weftkit-processor"))
    compileOnly(libs.bukkit)

    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(project(":weftkit-processor"))
    testImplementation(libs.bukkit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.compile.testing)
}

// Stamps the build version into the resource WeftkitMetrics reports to bStats
tasks.named<Copy>("processResources") {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("weftkit-version.properties") {
        expand("version" to version)
    }
}
