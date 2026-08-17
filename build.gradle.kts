plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.maven.publish) apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-classfile", "-Werror"))
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            palantirJavaFormat("2.90.0")
        }
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    // The POM carries the same dependency info. Skipping to keep releases under Maven Central's monthly file-count limit
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.withType<JacocoReport>())
    }

    tasks.withType<JacocoReport>().configureEach {
        reports.xml.required.set(true)
    }

    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral()
        // Sign only when the release workflow supplies the key, so local publishing stays credential-free;
        // Central rejects unsigned uploads, so a missing key cannot slip through to a release
        if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
        pom {
            name.set(project.name)
            // Each module sets project.description; fail loudly if a new one forgets
            description.set(provider {
                checkNotNull(project.description) { "Set description in ${project.name}/build.gradle.kts" }
            })
            url.set("https://weftkit.org")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("CorneliusMa")
                    name.set("Cornelius Mayer")
                }
            }
            scm {
                url.set("https://github.com/weftkit/weftkit")
                connection.set("scm:git:https://github.com/weftkit/weftkit.git")
                developerConnection.set("scm:git:ssh://git@github.com/weftkit/weftkit.git")
            }
        }
    }
}
