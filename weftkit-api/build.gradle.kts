plugins {
    `java-test-fixtures`
}

description = "The weftkit wiring API: annotations, lifecycle interfaces, and the generated registry contract"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
afterEvaluate {
    configurations.findByName("testFixturesSourcesElements")?.let {
        javaComponent.withVariantsFromConfiguration(it) { skip() }
    }
}
