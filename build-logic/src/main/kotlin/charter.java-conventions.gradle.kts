// Every Java module: one toolchain, warnings as errors, JUnit Platform, the shared BOMs.
plugins {
    `java-library`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(charterJavaVersion) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = charterJavaVersion
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Werror", "-parameters"))
}

dependencies {
    testImplementation(platform(catalog.lib("junit-bom")))
    testImplementation(catalog.lib("junit-jupiter"))
    testImplementation(catalog.lib("assertj"))
    testRuntimeOnly(catalog.lib("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
