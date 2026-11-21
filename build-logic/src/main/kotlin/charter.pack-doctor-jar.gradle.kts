// The pack-doctor jar (ADR-0075 decision 9, as amended 2026-11-13; #478, WP06): one runnable file,
// build/pack-doctor/pack-doctor-<version>.jar, with no Spring in it (NFR-001) and under 40 MB (NFR-004), and the jar test
// that runs it in a child JVM inside `check`, so in `verifyAll`. Applied by service/pack-doctor alone.
//
// - The classpath (T023): `packDoctorJarClasspath` holds runtimeClasspath's declared dependencies minus three modules,
//   excluded by module, never by group (squad J5): spring-boot-starter-webmvc, :web-transport and :charter-api, the
//   reach of :packs into Spring, Tomcat, Jakarta, Micrometer and the logging backends. A group exclude of
//   org.springframework.boot would drop spring-boot-dependencies, the BOM jackson-databind and slf4j-api take their
//   versions from. It resolves consistently with runtimeClasspath, so the jar holds the versions the module's tests
//   run, and the jar test compares the two lists. The module's ModuleDependencyAllowlist judges it too (J6).
// - The jar (T024): the module's classes and every library's entries, with the duplicates refused. Left out: every
//   module-info.class (the jar is on the class path, never the module path), the libraries' manifests and signature
//   files, and :packs' DNS-rebinding guard (META-INF/services/java.net.spi.InetAddressResolverProvider): the CLI does
//   no network I/O. SLF4J gets the NOP provider (research R2) from a service file generated under build/generated,
//   never from src/main/resources: deployable's tests put :pack-doctor beside Logback, and a second provider breaks
//   Spring Boot's logging there (J1). The jar goes to build/pack-doctor/, never build/libs, the thin jar's (J7).
// - The licences (T025): PackDoctorLicences moves each library's licence and notice files under
//   META-INF/licenses/<group>-<module>/ and fails on a library with none (J8).
// - The jar test (T026): `jarTest` runs the tests tagged `jar` (PackDoctorJarTest), which `test` and so PIT leave out
//   (J9, J10). It runs the jar with the toolchain's launcher, and compares it with a second build of the same jar
//   (packDoctorJarAgain), byte for byte (J12).
plugins {
    java
}

interface PackDoctorJarServices {
    @get:javax.inject.Inject
    val archives: ArchiveOperations
}

val archives = objects.newInstance<PackDoctorJarServices>().archives
val mainClass = "ai.speckitty.charter.packdoctor.PackDoctorMain"
val jarClasspathName = "packDoctorJarClasspath"

// Named before the configuration exists, so the module's allowlist judges it whenever it is enforced (J6).
ModuleDependencyAllowlist.judgeAlso(project, jarClasspathName)
afterEvaluate {
    if (!ModuleDependencyAllowlist.isEnforcedOn(project)) {
        throw GradleException("${project.path} applies charter.pack-doctor-jar but enforces no ModuleDependencyAllowlist (#478)")
    }
}

val runtimeClasspath = configurations.runtimeClasspath
val packDoctorJarClasspath = configurations.create(jarClasspathName) {
    description = "pack-doctor's runtime classpath with no Spring, Tomcat, Jakarta or logging backend: the jar's (#478)."
    isCanBeConsumed = false
    isCanBeResolved = true
    // runtimeClasspath's own hierarchy: the same declared dependencies.
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
    shouldResolveConsistentlyWith(runtimeClasspath.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, charterJavaVersion)
    }
    // By module, never by group (J5).
    exclude(mapOf("group" to "org.springframework.boot", "module" to "spring-boot-starter-webmvc"))
    exclude(mapOf("module" to "web-transport"))
    exclude(mapOf("module" to "charter-api"))
}

// SLF4J's NOP provider, in the jar alone (J1, research R2).
val slf4jProviderDirectory = layout.buildDirectory.dir("generated/pack-doctor-jar/slf4j")
val packDoctorSlf4jProvider = tasks.register("packDoctorSlf4jProvider") {
    description = "Writes the SLF4J service file that names the NOP provider, for the pack-doctor jar alone (#478)."
    val directory = slf4jProviderDirectory
    outputs.dir(directory).withPropertyName("slf4jProvider")
    doLast {
        val file = directory.get().file("META-INF/services/org.slf4j.spi.SLF4JServiceProvider").asFile
        file.parentFile.mkdirs()
        file.writeText("org.slf4j.helpers.NOP_FallbackServiceProvider\n")
    }
}

val packDoctorLicences = tasks.register<PackDoctorLicences>("packDoctorLicences") {
    description = "Moves each library's licence files under META-INF/licenses/<group>-<module>/ for the pack-doctor jar (#478)."
    artifacts = packDoctorJarClasspath.incoming.artifacts.resolvedArtifacts
    artifactFiles.from(packDoctorJarClasspath)
    checkedIn = layout.projectDirectory.dir("licenses")
    projectLicence = rootProject.layout.projectDirectory.file("LICENSE")
    outputDirectory = layout.buildDirectory.dir("generated/pack-doctor-jar/licences")
}

val mainOutput = sourceSets.main.map { it.output }
// A local, so the lambda captures the service and never the script (configuration cache).
val libraryTrees = run {
    val zips = archives
    packDoctorJarClasspath.elements.map { jars -> jars.map { zips.zipTree(it.asFile) } }
}
val implementationVersion = project.version.toString()

fun Jar.packDoctorContents() {
    archiveBaseName = "pack-doctor"
    manifest {
        attributes(
            "Main-Class" to mainClass,
            "Multi-Release" to "true",
            "Implementation-Title" to "pack-doctor",
            "Implementation-Version" to implementationVersion,
        )
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(mainOutput)
    from(packDoctorSlf4jProvider)
    from(packDoctorLicences)
    from(libraryTrees) {
        exclude("**/module-info.class")
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/services/java.net.spi.InetAddressResolverProvider")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
        // Moved under META-INF/licenses/ by packDoctorLicences, so they never collide.
        exclude { PackDoctorLicences.isLicence(it.path) }
    }
}

val packDoctorJar = tasks.register<Jar>("packDoctorJar") {
    group = "build"
    description = "Builds build/pack-doctor/pack-doctor-<version>.jar, the runnable Spring-free pack-doctor (#478)."
    destinationDirectory = layout.buildDirectory.dir("pack-doctor")
    packDoctorContents()
}

// The same jar again, for the jar test's byte-for-byte comparison (J12).
val packDoctorJarAgain = tasks.register<Jar>("packDoctorJarAgain") {
    description = "Builds the pack-doctor jar a second time, which jarTest compares with packDoctorJar byte for byte (#478)."
    destinationDirectory = layout.buildDirectory.dir("pack-doctor-again")
    packDoctorContents()
}

tasks.assemble { dependsOn(packDoctorJar) }

val runtimeVersions = tasks.register<ModuleVersions>("packDoctorRuntimeVersions") {
    description = "Lists runtimeClasspath's libraries and versions, which jarTest compares with the jar's (#478, J5)."
    artifacts = runtimeClasspath.flatMap { it.incoming.artifacts.resolvedArtifacts }
    output = layout.buildDirectory.file("generated/pack-doctor-jar/runtime-classpath.txt")
}

// The jar's own tests run in jarTest alone, never in test or PIT (which takes test's excluded tags).
tasks.test {
    useJUnitPlatform { excludeTags("jar") }
}

val requireSymlinks = providers.gradleProperty("requireSymlinks").orElse("false")
val launcher = javaToolchains.launcherFor(java.toolchain)
val testSourceSet = sourceSets.test.get()
val jarTest = tasks.register<Test>("jarTest") {
    group = "verification"
    description = "Runs the pack-doctor jar in a child JVM over every path, and checks its entries (#478)."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("jar") }
    shouldRunAfter(tasks.test)
    val jar = packDoctorJar.flatMap { it.archiveFile }
    val again = packDoctorJarAgain.flatMap { it.archiveFile }
    val versions = runtimeVersions.flatMap { it.output }
    inputs.file(jar).withPropertyName("packDoctorJar").withPathSensitivity(PathSensitivity.NONE)
    inputs.file(again).withPropertyName("packDoctorJarAgain").withPathSensitivity(PathSensitivity.NONE)
    inputs.file(versions).withPropertyName("runtimeVersions").withPathSensitivity(PathSensitivity.NONE)
    inputs.property("requireSymlinks", requireSymlinks)
    jvmArgumentProviders.add(
        PackDoctorJarTestArguments(
            jar.map { it.asFile.absolutePath },
            again.map { it.asFile.absolutePath },
            versions.map { it.asFile.absolutePath },
            launcher.map { it.executablePath.asFile.absolutePath },
            requireSymlinks,
        ),
    )
}

tasks.check { dependsOn(jarTest) }
