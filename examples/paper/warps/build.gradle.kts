plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "org.weftkit.examples"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    implementation("org.weftkit:weftkit-paper:0.6.0")
    annotationProcessor("org.weftkit:weftkit-processor:0.6.0")
    annotationProcessor("org.weftkit:weftkit-paper-processor:0.6.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.weftkit.wiring", "org.weftkit.examples.warps.lib.weftkit")
    minimize {}
    metaInf { from(rootProject.layout.projectDirectory.file("../../../LICENSE")) }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
