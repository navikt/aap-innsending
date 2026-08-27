rootProject.name = "innsending"
include("app")

includeBuild("build-logic")

dependencyResolutionManagement {
    // Felles for alle gradle prosjekter i repoet
    @Suppress("UnstableApiUsage")
    repositories {
        // Nav-interne artefakter hentes kun herfra, for å unngå unødvendig oppslag i andre repoer
        exclusiveContent {
            forRepository {
                maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
            }
            filter {
                includeGroupByRegex("no\\.nav\\..*")
            }
        }
        mavenCentral()
        mavenLocal()
    }
}
