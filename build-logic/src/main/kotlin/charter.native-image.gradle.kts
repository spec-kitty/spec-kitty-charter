// The native executable of the service (ADR-0046 decision 5): Spring AOT plus GraalVM native-image. Applied beside
// `charter.service-conventions`; next to the Spring Boot plugin it also registers processAot, adds the AOT output to
// bootJar and builds nativeCompile.
//
// The executable is named as core's service pin expects before a release adds the version and the target
// (ADR-0046 decision 4). native-image comes from the GraalVM on JAVA_HOME (CI: graalvm/setup-graalvm); a JDK without
// it fails only nativeCompile, never verifyAll. The reachability-metadata repository supplies the hints of
// third-party libraries; the service's own hints are in code (CharterRuntimeHints).
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("org.graalvm.buildtools.native")
}

graalvmNative {
    toolchainDetection = false
    metadataRepository { enabled = true }
    binaries.named("main") {
        imageName = "spec-kitty-charter-service"
        buildArgs.add("--no-fallback")
    }
}

// The AOT source sets (aot, aotTest) hold code Spring generates from the bean definitions, not hand-written code, so
// they are outside the hand-written gates by construction, as the generated contract is (charter.java-base): no lint
// as errors, no Error Prone, no Checkstyle. The hand-written main and test sources keep every gate.
val aotSourceSets = setOf("aot", "aotTest")
tasks.withType<JavaCompile>().matching { it.name in setOf("compileAotJava", "compileAotTestJava") }.configureEach {
    options.compilerArgs.removeAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone.enabled = false
}
tasks.withType<Checkstyle>().matching { task -> aotSourceSets.any { task.name == "checkstyle" + it.replaceFirstChar(Char::titlecase) } }.configureEach {
    enabled = false
}
