description = "The weftkit component loader that drives generated registries at runtime"

dependencies {
    api(project(":weftkit-annotations"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testAnnotationProcessor(project(":weftkit-processor"))
}
