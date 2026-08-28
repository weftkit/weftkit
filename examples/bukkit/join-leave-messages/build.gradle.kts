plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "org.weftkit.examples"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    implementation("org.weftkit:weftkit-bukkit:0.6.0")
    annotationProcessor("org.weftkit:weftkit-processor:0.6.0")
    annotationProcessor("org.weftkit:weftkit-bukkit:0.6.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.weftkit.wiring", "org.weftkit.examples.joinleave.lib.weftkit")
    relocate("org.bstats", "org.weftkit.examples.joinleave.lib.bstats")
    minimize {}
    metaInf { from(rootProject.layout.projectDirectory.file("../../../LICENSE")) }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
