description = "The weftkit annotation processor that validates the wiring graph and generates the registry"

dependencies {
    implementation(project(":weftkit-annotations"))

    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(project(":weftkit-runtime"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.compile.testing)
}
