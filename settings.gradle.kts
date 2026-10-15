rootProject.name = "innsending"
include("app")

includeBuild("build-logic")

dependencyResolutionManagement {
    // Felles for alle gradle prosjekter i repoet
    versionCatalogs {
        create("kelvinLibs") {
            from("no.nav.aap.kelvin:version-catalog:2.0.186")
        }
    }
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
