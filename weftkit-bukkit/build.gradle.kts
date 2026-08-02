dependencies {
    api(project(":weftkit-runtime"))
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
