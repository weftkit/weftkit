description = "The weftkit Paper adapter: Brigadier command registration on top of the Bukkit adapter"

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    api(project(":weftkit-bukkit"))
    compileOnly(libs.paper)

    testImplementation(libs.paper)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
