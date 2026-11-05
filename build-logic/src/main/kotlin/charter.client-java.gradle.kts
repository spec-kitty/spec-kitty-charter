// The Java client of the charter contract (#217, #218): openapi-generator's `java` generator with its `okhttp-gson`
// library, run on the single-file bundle the contract build publishes, plus one hand-written class (ProblemDetails).
// The generated sources live in their own source set, `generated`, outside every gate, as Spring's AOT sources do in
// charter.native-image; the hand-written main and test sources keep every gate of `charter.hand-written`.
// Everything compiles with --release 21 on the build's toolchain: consumers run Java 21 or 25 (operator, #217).
import net.ltgt.gradle.errorprone.errorprone
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("charter.java-base")
    id("charter.hand-written")
    id("org.openapi.generator")
}

// Coordinates (operator, #217): group ai.speckitty.charter, artifact charter-client, package
// ai.speckitty.charter.client. The client's version is the contract's info.version; nothing is published yet (#220).
group = "ai.speckitty.charter"
base.archivesName = "charter-client"
version = contractInfoVersion.get()

val consumerRelease = 21
tasks.withType<JavaCompile>().configureEach { options.release = consumerRelease }

// The bundle, through the `contractBundle` variant :contracts publishes (its YAML and its .sha256). The generator
// reads the YAML only.
val contractBundle = configurations.dependencyScope("contractBundle")
val contractBundleFiles = configurations.resolvable("contractBundleFiles") {
    extendsFrom(contractBundle.get())
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named("charter-contract-bundle")) }
}
dependencies {
    "contractBundle"(project(":contracts"))
}
val bundle = layout.file(
    contractBundleFiles.flatMap { it.elements }
        .map { files -> files.single { it.asFile.name == "charter-openapi.yaml" }.asFile },
)

val templates = layout.projectDirectory.dir("templates")

/** Generates the client from the bundle into [dir]; the build generates twice to prove the output reproducible. */
fun GenerateTask.client(dir: Provider<Directory>) {
    group = "build"
    inputs.files(contractBundleFiles).withPathSensitivity(PathSensitivity.NAME_ONLY).withPropertyName("contractBundle")
    inputs.dir(templates).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("templates")
    generatorName = "java"
    library = "okhttp-gson"
    inputSpec = bundle
    outputDir = dir
    // One override, the file header: SPDX licence and the do-not-edit note (operator, #217).
    templateDir = templates
    invokerPackage = "ai.speckitty.charter.client"
    apiPackage = "ai.speckitty.charter.client.api"
    modelPackage = "ai.speckitty.charter.client.model"
    cleanupOutput = true
    generateApiTests = false
    generateModelTests = false
    generateApiDocumentation = false
    generateModelDocumentation = false
    // Reads every closed object as open, so a member a later 0.x minor adds is kept, not refused; folds #119's copies.
    openapiNormalizer = mapOf("NORMALIZER_CLASS" to TolerantClientNormalizer::class.java.name)
    configOptions = mapOf(
        "sourceFolder" to "src/main/java",
        // An enum value a later 0.x minor adds decodes to UNKNOWN_DEFAULT_OPEN_API instead of failing the decode.
        "enumUnknownDefaultCase" to "true",
        // An object that declares no additionalProperties is open as well.
        "disallowAdditionalPropertiesIfNotPresent" to "false",
        "openApiNullable" to "false",
        "dateLibrary" to "java8",
        "hideGenerationTimestamp" to "true",
        "useJakartaEe" to "true",
        "annotationLibrary" to "none",
        "documentationProvider" to "none",
        "serializableModel" to "false",
    )
}

val generatedDir = layout.buildDirectory.dir("generated/client")
val generateClient = tasks.register<GenerateTask>("generateClient") {
    description = "Generates the Java client (okhttp-gson) from the bundled charter contract."
    client(generatedDir)
}

val generated = sourceSets.create("generated") {
    java.srcDir(files(generatedDir.map { it.dir("src/main/java") }).builtBy(generateClient))
}

// Generated code is outside the gates, by name, as in charter.native-image (both are named in the register of
// exemptions at the head of charter.hand-written): no lint as errors, no Error Prone, no
// Checkstyle. Spotless only ever reads src/.
tasks.named<JavaCompile>(generated.compileJavaTaskName) {
    options.compilerArgs.removeAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone.enabled = false
}
tasks.named("checkstyle" + generated.name.replaceFirstChar(Char::titlecase)) { enabled = false }

// The client's runtime: what the generated sources import, pinned in the catalog. `api`, because the generated
// ApiClient exposes okhttp and gson types; the generated source set compiles against the same set.
configurations.named(generated.compileClasspathConfigurationName) { extendsFrom(configurations["api"]) }
dependencies {
    "api"(catalog.lib("okhttp"))
    "api"(catalog.lib("okhttp-logging-interceptor"))
    "api"(catalog.lib("gson"))
    "api"(catalog.lib("gson-fire"))
    "api"(catalog.lib("jakarta-annotation-api-client"))
    "implementation"(generated.output)
}

// One jar and one sources jar for the client: the generated classes beside the hand-written one.
tasks.jar { from(generated.output) }
tasks.named<Jar>("sourcesJar") { from(generated.allJava) }

// Generating again from the same bundle gives the same bytes: no timestamp, no unordered output (#218). The second
// generation is never taken from the build cache, which would restore the first one's entry (same inputs) and prove
// nothing; it runs in the same JVM as the first, so the check does not cover nondeterminism across JVMs.
val generatedAgainDir = layout.buildDirectory.dir("generated/client-again")
val generateClientAgain = tasks.register<GenerateTask>("generateClientAgain") {
    description = "Generates the client a second time, for checkClientReproducible."
    client(generatedAgainDir)
    outputs.cacheIf("a second generation must run, not be restored from the first one's cache entry") { false }
}
val checkClientReproducible = tasks.register<CheckTreesIdentical>("checkClientReproducible") {
    group = "verification"
    description = "Fails unless two generations of the client from the same bundle are byte-identical."
    first.set(generatedDir.map { it.dir("src/main/java") })
    second.set(generatedAgainDir.map { it.dir("src/main/java") })
    dependsOn(generateClient, generateClientAgain)
    marker = layout.buildDirectory.file("reports/client-reproducible.txt")
}

/** Hands the generated sources and every class directory of the client to the tests that check them. */
abstract class ClientOutputArguments : CommandLineArgumentProvider {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ConfigurableFileCollection

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val generatedClasses: ConfigurableFileCollection

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val mainClasses: ConfigurableFileCollection

    @get:Input abstract val contractVersion: Property<String>

    @get:Input abstract val release: Property<Int>

    @get:Input abstract val generatorVersion: Property<String>

    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val howTo: RegularFileProperty

    override fun asArguments(): List<String> = listOf(
        "-Dcharter.client.generated-sources=" + sources.files.joinToString(File.pathSeparator),
        "-Dcharter.client.generated-classes=" + generatedClasses.files.joinToString(File.pathSeparator),
        "-Dcharter.client.main-classes=" + mainClasses.files.joinToString(File.pathSeparator),
        "-Dcharter.client.contract-version=" + contractVersion.get(),
        "-Dcharter.client.release=" + release.get(),
        "-Dcharter.client.generator-version=" + generatorVersion.get(),
        "-Dcharter.client.how-to=" + howTo.get().asFile.absolutePath,
    )
}

tasks.test {
    jvmArgumentProviders.add(objects.newInstance<ClientOutputArguments>().apply {
        sources.from(files(generatedDir.map { it.dir("src/main/java") }).builtBy(generateClient))
        release = consumerRelease
        generatorVersion = catalog.version("openapi-generator")
        // The how-to names the generator version a reader must use; HowToTest fails when a bump leaves it stale.
        howTo = rootProject.layout.projectDirectory.file("docs/guides/generate-a-client.md")
        generatedClasses.from(generated.output.classesDirs)
        mainClasses.from(sourceSets.main.map { it.output.classesDirs })
        contractVersion = contractInfoVersion
    })
}

tasks.named("check") { dependsOn(checkClientReproducible) }
