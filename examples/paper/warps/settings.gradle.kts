rootProject.name = "warps"

// Substitutes the org.weftkit dependencies with the sources in this repository.
// Remove this line when using the example as a template outside the repository.
includeBuild("../../..")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
