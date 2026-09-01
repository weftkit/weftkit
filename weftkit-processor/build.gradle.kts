description = "The weftkit annotation processor that validates the wiring graph and generates the registry"

dependencies {
    implementation(project(":weftkit-api"))
    implementation(libs.javapoet)

    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.compile.testing)
    testImplementation(project(":weftkit-runtime"))
}
