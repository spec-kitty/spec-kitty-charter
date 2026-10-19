// Every Java module, generated or hand-written: one toolchain, JUnit Platform, the shared BOMs.
// It holds no quality bar; hand-written modules add every gate with `charter.hand-written`, so generated
// code (charter-api) is exempt by construction rather than by removing flags it inherited.
plugins {
    `java-library`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(charterJavaVersion) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = charterJavaVersion
    options.compilerArgs.add("-parameters")
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
