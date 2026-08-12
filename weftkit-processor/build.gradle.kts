description = "The weftkit annotation processor that validates the wiring graph and generates the registry"

dependencies {
    implementation(project(":weftkit-annotations"))
    implementation(project(":weftkit-runtime"))
    implementation(libs.javapoet)

    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.compile.testing)
}
