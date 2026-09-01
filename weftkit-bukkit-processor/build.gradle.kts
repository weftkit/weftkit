description = "Compile-time support for the Bukkit adapter: bundled module contribution and validation rules"

dependencies {
    implementation(project(":weftkit-processor"))

    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(libs.bukkit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.compile.testing)
    testImplementation(project(":weftkit-bukkit"))
}
