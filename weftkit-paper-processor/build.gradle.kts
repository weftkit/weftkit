description = "Compile-time support for the Paper adapter: bundled module contribution and validation rules"

val java21 = JavaLanguageVersion.of(21)

tasks.compileTestJava {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(java21) })
}

tasks.test {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(java21) })
}

dependencies {
    implementation(project(":weftkit-api"))
    implementation(project(":weftkit-processor"))
    runtimeOnly(project(":weftkit-bukkit-processor"))

    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(libs.paper)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.compile.testing)
    testImplementation(project(":weftkit-paper"))
}
