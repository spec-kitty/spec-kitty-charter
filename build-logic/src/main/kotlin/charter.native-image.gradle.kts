// The native executable of the service (ADR-0046 decision 5): Spring AOT plus GraalVM native-image, applied beside
// `charter.service-conventions` only for a native build, that is with -Pcharter.native=true (the native CI jobs,
// .github/workflows/service-native.yml):
//
//   ./gradlew :deployable:nativeCompile -Pcharter.native=true
//
// Every other build, verifyAll and bootJar included, runs without Spring AOT. Next to the Spring Boot plugin the native
// plugin registers processAot and processTestAot and wires processTestAot into the JVM test task; processTestAot starts
// every test's Spring context, and that load, in parallel with the other modules' tests, broke a CPU-time budget test.
// The jar runs on the JVM and needs no AOT; what the native executable needs is proven by starting it in CI
// (scripts/release/smoke-service.sh), and the service's own hints are unit-tested (CharterRuntimeHintsTest).
//
// The executable is named as core's service pin expects before a release adds the version and the target
// (ADR-0046 decision 4). native-image comes from the GraalVM on JAVA_HOME (CI: graalvm/setup-graalvm). The
// reachability-metadata repository (on by default) supplies the hints of third-party libraries.
import net.ltgt.gradle.errorprone.errorprone
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension

if (providers.gradleProperty("charter.native").orNull == "true") {
    apply(plugin = "org.graalvm.buildtools.native")

    extensions.configure<GraalVMExtension> {
        toolchainDetection = false
        binaries.named("main") {
            imageName = "spec-kitty-charter-service"
            // An executable, not a shared library: the plugin defaults to a library in a java-library project, which
            // every module is (charter.java-base), and an executable needs the main class, bootJar's.
            sharedLibrary = false
            mainClass = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").flatMap { it.mainClass }
            // native-image's own heap: a Spring Boot web image needs several GB, and the macOS runner has 7 GB in all.
            buildArgs.add("-J-Xmx5g")
        }
    }

    // The AOT source sets (aot, aotTest) hold code Spring generates from the bean definitions, not hand-written code,
    // so they are outside the hand-written gates by construction, as the generated contract is (charter.java-base): no
    // lint as errors, no Error Prone, no Checkstyle. The hand-written main and test sources keep every gate.
    val aotSourceSets = setOf("aot", "aotTest")
    tasks.withType<JavaCompile>().matching { it.name in setOf("compileAotJava", "compileAotTestJava") }.configureEach {
        options.compilerArgs.removeAll(listOf("-Xlint:all", "-Werror"))
        options.errorprone.enabled = false
    }
    // Disabled and emptied: a disabled task still runs what it depends on.
    tasks.withType<Checkstyle>()
        .matching { task -> aotSourceSets.any { task.name == "checkstyle" + it.replaceFirstChar(Char::titlecase) } }
        .configureEach {
            enabled = false
            setSource(files())
            classpath = files()
        }
}
