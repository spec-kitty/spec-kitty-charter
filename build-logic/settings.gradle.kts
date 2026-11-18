// Convention plugins for the charter service build. Included by the root settings.
// Repositories as the root settings declare them for plugins (ADR-0079): Central's mirror, then the plugin portal.
// pluginManagement is declared too: without it, the `kotlin-dsl` plugin's classpath (the Kotlin Gradle plugin and
// stdlib) resolves through the portal alone, which hands it to Central.
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"
