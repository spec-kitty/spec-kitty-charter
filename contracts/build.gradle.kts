// The charter contract's own checks (DM 01M57WH7P0QZWHKYGBA2R0MA3K): the single-file bundle core vendors,
// lint with the charter's ruleset and the planted fixtures of every rule, the closed-response-objects
// checker (Java, src/main/java; DM 01M584Q3NNK0B78H9NKHV7VJDK) and the breaking-change check against the
// baseline release the release gate names (Java, src/main/java; DM 01M5986KJ7B18K7GQ25E47J989) with its own
// planted pairs. All run in `check`, so `./gradlew verifyAll` runs them.
// Conventions and how to add a rule: contracts/lint/README.md.
//
// The tools are release binaries pinned below by version and SHA-256 per platform; a download whose
// checksum does not match fails the build before anything is extracted.
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

plugins {
    id("charter.java-base")
    id("charter.hand-written")
}

dependencies {
    implementation(libs.snakeyaml.engine)
}

// This project builds into the root build/contracts, not contracts/build. That is not what keeps :charter-api's
// code generation clean: it reads only contracts/charter and contracts/_shared (charter.contract-api.gradle.kts),
// so contracts/build would not be among its inputs either. The location stays because CI uploads the bundle from
// build/contracts/contract/ (.github/workflows/service.yml) and lint/README.md names that path.
layout.buildDirectory = rootProject.layout.buildDirectory.dir("contracts")

/** One pinned release archive of a tool and the name of the executable inside it. */
data class ToolPin(val url: String, val sha256: String, val executable: String)

// Versions released 2026-09-15. Each checksum was checked against the vendor's checksums.txt of the release.
val vacuumVersion = "0.30.6"
val oasdiffVersion = "1.32.1"
val vacuumRelease = "https://github.com/daveshanley/vacuum/releases/download/v$vacuumVersion"
val oasdiffRelease = "https://github.com/oasdiff/oasdiff/releases/download/v$oasdiffVersion"
val toolPins: Map<String, Map<String, ToolPin>> = mapOf(
    "vacuum" to mapOf(
        "linux-x86_64" to ToolPin("$vacuumRelease/vacuum_${vacuumVersion}_linux_x86_64.tar.gz",
            "179f038a71cd88721738d6c739dd07fbeebc905b48301935f2416e66aead95c6", "vacuum"),
        "linux-aarch64" to ToolPin("$vacuumRelease/vacuum_${vacuumVersion}_linux_arm64.tar.gz",
            "4e8389c0ad529b7bcdcfcff7e293f47adaf0e61a09e03186d5164dfd0a29cbe4", "vacuum"),
        "mac-x86_64" to ToolPin("$vacuumRelease/vacuum_${vacuumVersion}_darwin_x86_64.tar.gz",
            "1f862a54ea4067c6ca2b7213769ffc0fd5fa938565dae3d41559a0f408199077", "vacuum"),
        "mac-aarch64" to ToolPin("$vacuumRelease/vacuum_${vacuumVersion}_darwin_arm64.tar.gz",
            "2725a960bc3677c111c6659636e57dfbc899b266064c425d4410de169a681e4e", "vacuum"),
    ),
    "oasdiff" to mapOf(
        "linux-x86_64" to ToolPin("$oasdiffRelease/oasdiff_${oasdiffVersion}_linux_amd64.tar.gz",
            "7c8939fc49b75ee11fec66a5b83b37a2fca6aee109fed85013b1ba2ac2a1ee7f", "oasdiff"),
        "linux-aarch64" to ToolPin("$oasdiffRelease/oasdiff_${oasdiffVersion}_linux_arm64.tar.gz",
            "32fff58a120f75a723d6c2422444691c37fa6813fed61d23f53dbcb604b30f6d", "oasdiff"),
        // One universal archive for both macOS architectures.
        "mac-x86_64" to ToolPin("$oasdiffRelease/oasdiff_${oasdiffVersion}_darwin_all.tar.gz",
            "e4d74b7e2dfb9d4819e7fc720c905ec86547e4637ac270a2b0187c0f1fb7187e", "oasdiff"),
        "mac-aarch64" to ToolPin("$oasdiffRelease/oasdiff_${oasdiffVersion}_darwin_all.tar.gz",
            "e4d74b7e2dfb9d4819e7fc720c905ec86547e4637ac270a2b0187c0f1fb7187e", "oasdiff"),
    ),
)

val platform: String = run {
    val os = providers.systemProperty("os.name").get().lowercase()
    val arch = when (providers.systemProperty("os.arch").get().lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> "unsupported"
    }
    when {
        os.startsWith("linux") -> "linux-$arch"
        os.startsWith("mac") -> "mac-$arch"
        else -> "unsupported-$os"
    }
}

fun pinFor(tool: String): ToolPin = toolPins.getValue(tool)[platform] ?: throw GradleException(
    "No pinned $tool binary for platform '$platform'. Pinned platforms: ${toolPins.getValue(tool).keys.sorted()}. " +
        "Add one to contracts/build.gradle.kts with the checksum from the release's checksums.txt.")

/**
 * Downloads one pinned archive, verifies its SHA-256, and only then extracts its executable. Cacheable, so CI
 * restores the tool from the Gradle build cache (setup-gradle keeps it) instead of downloading it again: the cache
 * key holds the pinned URL and checksum, and the outputs are the verified archive and the extracted executable.
 * The build cache is trusted the way class outputs are, not for the integrity of a tool that gets executed:
 * [VerifyTool] re-hashes the archive against the pin on every build, restored or not, and every consumer runs
 * only after it.
 */
@CacheableTask
abstract class InstallTool @Inject constructor(
    private val archives: ArchiveOperations,
    private val files: FileSystemOperations,
) : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val executable: Property<String>
    @get:OutputFile abstract val archive: RegularFileProperty
    @get:OutputDirectory abstract val installDir: DirectoryProperty

    @TaskAction
    fun install() {
        val dir = installDir.get().asFile
        files.delete { delete(dir) }
        dir.mkdirs()
        val verified = archive.get().asFile
        verified.parentFile.mkdirs()
        val download = File(temporaryDir, "download.tar.gz")
        try {
            val connection = URI(url.get()).toURL().openConnection()
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.getInputStream().use { input ->
                Files.copy(input, download.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: SocketTimeoutException) {
            throw GradleException("Downloading ${url.get()} timed out (connect ${CONNECT_TIMEOUT_MS / 1000} s, " +
                "read ${READ_TIMEOUT_MS / 1000} s without data): ${e.message}", e)
        } catch (e: IOException) {
            throw GradleException("Downloading ${url.get()} failed: $e", e)
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(download.readBytes()).joinToString("") { "%02x".format(it) }
        if (actual != sha256.get()) {
            download.delete()
            throw GradleException("Checksum mismatch for ${url.get()}: expected ${sha256.get()}, got $actual. Nothing was extracted.")
        }
        Files.move(download.toPath(), verified.toPath(), StandardCopyOption.REPLACE_EXISTING)
        files.copy {
            from(archives.tarTree(archives.gzip(verified))) { include(executable.get()) }
            into(dir)
        }
        val tool = File(dir, executable.get())
        if (!tool.isFile || !tool.setExecutable(true)) {
            throw GradleException("${url.get()} has no executable '${executable.get()}'")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
    }
}

/**
 * Re-verifies an installed tool on every build, whether [InstallTool] ran or its outputs were restored from the
 * build cache: the archive must hash to the pin, and the executable must be byte for byte the one in that
 * archive. Not cacheable and without outputs, so it runs every time; it is cheap (one hash and one extraction of a
 * few megabytes). Every [ContractToolTask] depends on it, so none runs a tool that was not re-verified.
 */
@DisableCachingByDefault(because = "Verifies what is on disk now, which no cache can vouch for.")
abstract class VerifyTool @Inject constructor(
    private val archives: ArchiveOperations,
    private val files: FileSystemOperations,
) : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val executable: Property<String>
    @get:Internal abstract val archive: RegularFileProperty
    @get:Internal abstract val installedExecutable: RegularFileProperty

    @TaskAction
    fun verify() {
        val actual = hex(archive.get().asFile)
        if (actual != sha256.get()) {
            throw GradleException("The installed archive ${archive.get().asFile} for ${url.get()} hashes to $actual, " +
                "not the pinned ${sha256.get()}: a restored or altered download. Run with " +
                "--rerun-tasks to download the pinned release again and replace the cached copy. Nothing was run.")
        }
        val fresh = File(temporaryDir, "extracted")
        files.delete { delete(fresh) }
        files.copy {
            from(archives.tarTree(archives.gzip(archive.get().asFile))) { include(executable.get()) }
            into(fresh)
        }
        val expected = File(fresh, executable.get())
        val installed = installedExecutable.get().asFile
        if (!expected.isFile || !installed.isFile || hex(installed) != hex(expected)) {
            throw GradleException("The installed ${installed.name} is not the executable in the verified archive " +
                "${archive.get().asFile.name}: a restored or altered tool. Run with " +
                "--rerun-tasks to install the pinned release again and replace the cached copy. Nothing was run.")
        }
    }

    private fun hex(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}

fun installTask(tool: String) = tasks.register<InstallTool>("install${tool.replaceFirstChar { it.uppercase() }}") {
    group = "contract"
    description = "Downloads the pinned $tool release for this platform and verifies its SHA-256."
    val pin = pinFor(tool)
    url = pin.url
    sha256 = pin.sha256
    executable = pin.executable
    archive = layout.buildDirectory.file("tools/archives/$tool.tar.gz")
    installDir = layout.buildDirectory.dir("tools/$tool")
}

fun verifyTask(tool: String, install: TaskProvider<InstallTool>) =
    tasks.register<VerifyTool>("verify${tool.replaceFirstChar { it.uppercase() }}") {
        group = "contract"
        description = "Re-verifies the installed $tool against the pinned SHA-256, also after a build cache restore."
        val pin = pinFor(tool)
        url = pin.url
        sha256 = pin.sha256
        executable = pin.executable
        archive = install.flatMap { it.archive }
        installedExecutable = install.flatMap { it.installDir.file(pin.executable) }
        dependsOn(install)
    }

val installVacuum = installTask("vacuum")
val installOasdiff = installTask("oasdiff")
val verifyVacuum = verifyTask("vacuum", installVacuum)
val verifyOasdiff = verifyTask("oasdiff", installOasdiff)
val vacuumExe = installVacuum.flatMap { it.installDir.file("vacuum") }
val oasdiffExe = installOasdiff.flatMap { it.installDir.file("oasdiff") }

/**
 * A task that runs the contract tools. Helpers live here, not at script level, so a task never captures the
 * build script (configuration cache).
 */
abstract class ContractToolTask : DefaultTask() {
    @get:Inject protected abstract val execOps: ExecOperations

    /** Runs a command, returning its exit code and combined output; never throws on a non-zero exit. */
    protected fun run(vararg command: String): Pair<Int, String> {
        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(*command)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        return result.exitValue to output.toString(Charsets.UTF_8)
    }

    /**
     * Bundles [source] into [out] with every `$ref` inlined: the one shape the contract is linted, vendored and
     * compared in. vacuum reports an unresolvable reference as an ERR line and still exits 0, and it keeps a
     * reference inside the same file, so both are checked; the result is null on success, else the reason.
     */
    protected fun bundle(vacuum: File, source: File, out: File): String? {
        out.parentFile.mkdirs()
        val (code, output) = run(vacuum.path, "bundle", "--no-style", "--no-update-check",
            "--base", source.parentFile.path, source.path, out.path)
        return when {
            code != 0 || output.lines().any { it.trimStart().startsWith("ERR") } -> output
            out.readText().contains("\$ref") -> "${out.name} still holds a \$ref; it must be self-contained"
            else -> null
        }
    }

    /**
     * The oasdiff breaking-change comparison every compatibility check uses: exit 1 means breaking. [levels] is
     * contracts/compat/severity-levels.txt; oasdiff reads no comments, so its `#` lines are dropped first.
     */
    protected fun breaking(oasdiff: File, levels: File, base: File, revision: File): Pair<Int, String> {
        val stripped = File(temporaryDir, "severity-levels.txt")
        stripped.writeText(levels.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .joinToString("\n", postfix = "\n"))
        // --flatten-allof: without it oasdiff judges each allOf part alone and reports an enum value added to a
        // composed response (a new refusal code in CharterRefusal) as a warning, which --fail-on ERR lets pass.
        return run(oasdiff.path, "breaking", "--flatten-allof", "--fail-on", "ERR", "--severity-levels", stripped.path,
            "--color", "never", base.path, revision.path)
    }

    protected fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}

// No consumer runs a tool that was not re-verified this build, whether it was downloaded or restored from the cache.
tasks.withType<ContractToolTask>().configureEach { dependsOn(verifyVacuum, verifyOasdiff) }

/** Bundles the split contract into one file with every $ref inlined, and checks the bundle is valid OpenAPI. */
@CacheableTask
abstract class BundleContract : ContractToolTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val contractFiles: ConfigurableFileCollection
    @get:Internal abstract val openapi: RegularFileProperty
    @get:Input abstract val openapiPath: Property<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val vacuum: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val oasdiff: RegularFileProperty
    @get:OutputFile abstract val bundle: RegularFileProperty
    @get:OutputFile abstract val checksum: RegularFileProperty

    @TaskAction
    fun bundleIt() {
        val out = bundle.get().asFile
        bundle(vacuum.get().asFile, openapi.get().asFile, out)?.let {
            throw GradleException("Bundling ${openapiPath.get()} failed:\n$it")
        }
        val (validCode, validation) = run(oasdiff.get().asFile.path, "validate", "--fail-on", "WARN",
            "--allow-external-refs=false", "--color", "never", out.path)
        if (validCode != 0) {
            throw GradleException("The bundle ${out.name} is not a valid OpenAPI document:\n$validation")
        }
        val digest = sha256(out)
        checksum.get().asFile.writeText("$digest  ${out.name}\n")
        logger.lifecycle("Bundled ${openapiPath.get()} into ${out.name} (sha256 $digest)")
    }
}

/** The rule ids of a ruleset: the keys directly under its `rules:` mapping. */
fun ruleIds(ruleset: File): List<String> {
    val ids = mutableListOf<String>()
    var inRules = false
    for (line in ruleset.readLines()) {
        if (line.startsWith("rules:")) { inRules = true; continue }
        if (inRules && line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#")) inRules = false
        val id = Regex("^  ([A-Za-z0-9-]+):").find(line)?.groupValues?.get(1)
        if (inRules && id != null) ids += id
    }
    return ids
}

/** Lints the bundle with the charter's ruleset; any violation fails the build. */
@CacheableTask
abstract class LintContract : ContractToolTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val ruleset: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val bundle: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val vacuum: RegularFileProperty
    @get:Input abstract val ruleCount: Property<Int>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun lint() {
        // The inlined bundle is large (#125 put it near 5 MB), and a rule over a recursive JSONPath needs more than
        // vacuum's default 5 seconds; a rule that times out is skipped, which the ERR check below refuses.
        val (code, output) = run(vacuum.get().asFile.path, "lint", "--no-style", "--no-banner", "--no-update-check",
            "--details", "--all-results", "--no-clip", "--timeout", "60", "--ruleset", ruleset.get().asFile.path,
            bundle.get().asFile.path)
        report.get().asFile.writeText(output)
        // A ruleset vacuum could not read would lint against no rules and pass; the rule count rules that out.
        val loaded = Regex("containing (\\d+) rules").find(output)?.groupValues?.get(1)?.toInt()
        if (loaded != ruleCount.get()) {
            throw GradleException("vacuum loaded ${loaded ?: "no"} rules from ${ruleset.get().asFile.name}; " +
                "it declares ${ruleCount.get()}:\n$output")
        }
        // A rule that times out is reported as "ERR Rule timed out, skipping"; both checks refuse it, so a skipped rule
        // never passes the contract in silence.
        if (code != 0 || output.lines().any { it.trimStart().startsWith("ERR") || it.contains("timed out") }) {
            throw GradleException("The charter contract breaks its lint rules:\n$output")
        }
        logger.lifecycle("The charter contract passes all ${ruleCount.get()} lint rules.")
    }
}

/**
 * Every planted fixture fails exactly its own rule; the clean fixture fails none; every rule has a fixture.
 * A fixture is `<rule id>.yaml` or `<rule id>.<case>.yaml`. Each is bundled first, through the same step as the
 * contract, so a rule is proven on the inlined shape it judges the contract in.
 */
@CacheableTask
abstract class LintFixtures : ContractToolTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val ruleset: RegularFileProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val fixtures: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val vacuum: RegularFileProperty
    @get:Input abstract val rules: ListProperty<String>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val byName = fixtures.files.associateBy { it.name.removeSuffix(".yaml") }
        val ruleOf = byName.keys.associateWith { it.substringBefore('.') }
        val problems = mutableListOf<String>()
        val missing = rules.get() - ruleOf.values.toSet()
        val stray = ruleOf.filterValues { it != "clean" && it !in rules.get() }.keys
        if (missing.isNotEmpty()) problems += "rules without a planted fixture: $missing"
        if (stray.isNotEmpty()) problems += "fixtures that name no rule: $stray"
        if ("clean" !in byName) problems += "no clean fixture (clean.yaml)"
        val lines = mutableListOf<String>()
        for ((name, file) in byName.toSortedMap()) {
            val bundled = File(temporaryDir, "$name.yaml")
            bundle(vacuum.get().asFile, file, bundled)?.let { problems += "$name: bundling failed: $it"; continue }
            val (code, output) = run(vacuum.get().asFile.path, "report", "--stdout", "--no-style",
                "--no-update-check", "--ruleset", ruleset.get().asFile.path, bundled.path)
            if (code != 0) { problems += "$name: vacuum report failed:\n$output"; continue }
            @Suppress("UNCHECKED_CAST")
            val results = ((JsonSlurper().parseText(output) as Map<String, Any?>)["resultSet"] as Map<String, Any?>?)
                ?.get("results") as List<Map<String, Any?>>? ?: emptyList()
            val fired = results.map { it["ruleId"] as String }.toSortedSet()
            val rule = ruleOf.getValue(name)
            val expected = if (rule == "clean") sortedSetOf() else sortedSetOf(rule)
            lines += "$name: ${if (fired.isEmpty()) "no violation" else fired.joinToString()}"
            if (fired != expected) problems += "$name: expected ${expected.ifEmpty { "no violation" }}, got ${fired.ifEmpty { "no violation" }}"
        }
        report.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("The lint fixtures do not match the ruleset:\n" + problems.joinToString("\n") { "- $it" })
        }
        logger.lifecycle("Each of ${rules.get().size} rules fails its ${byName.size - 1} planted fixtures, and only them; " +
            "the clean fixture passes.")
    }
}

/**
 * The compatibility check on planted pairs: each `compat/fixtures/<case>/` holds `base.yaml` and `revision.yaml`
 * (inlined documents, the shape bundles have). A `breaking-*` case must fail the comparison, a `compatible-*`
 * case must pass it, and there is at least one of each.
 */
@CacheableTask
abstract class CompatibilityFixtures : ContractToolTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val fixtures: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val severityLevels: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val oasdiff: RegularFileProperty
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val cases = fixtures.files.map { it.parentFile }.distinct().sortedBy { it.name }
        val problems = mutableListOf<String>()
        if (cases.none { it.name.startsWith("breaking-") }) problems += "no breaking-* case"
        if (cases.none { it.name.startsWith("compatible-") }) problems += "no compatible-* case"
        val lines = mutableListOf<String>()
        for (case in cases) {
            val base = File(case, "base.yaml")
            val revision = File(case, "revision.yaml")
            if (!base.isFile || !revision.isFile) { problems += "${case.name}: needs base.yaml and revision.yaml"; continue }
            val (code, output) = breaking(oasdiff.get().asFile, severityLevels.get().asFile, base, revision)
            val expected = when {
                case.name.startsWith("breaking-") -> 1
                case.name.startsWith("compatible-") -> 0
                else -> { problems += "${case.name}: name it breaking-* or compatible-*"; continue }
            }
            lines += "${case.name}: ${if (code == 1) "breaking" else if (code == 0) "compatible" else "error $code"}"
            if (code != expected) problems += "${case.name}: expected ${if (expected == 1) "breaking" else "compatible"}, " +
                "oasdiff exited $code:\n$output"
        }
        report.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("The compatibility fixtures do not hold:\n" + problems.joinToString("\n") { "- $it" })
        }
        logger.lifecycle("The compatibility check fails each of ${cases.count { it.name.startsWith("breaking-") }} " +
            "breaking cases and passes each of ${cases.count { it.name.startsWith("compatible-") }} compatible cases.")
    }
}

/**
 * The oasdiff breaking-change check of the bundle against the bundle of a released contract. Which release is the
 * baseline is the release gate's decision (`ai.speckitty.charter.contracts.compat.ReleaseGate`, whose class Javadoc
 * is the authority; DM 01M5986KJ7B18K7GQ25E47J989): `info.version` equals the top CHANGELOG heading, only
 * `contract-charter-v*` tags merged into HEAD count, only a release of the same line is a baseline (the same major
 * version, and while the major is 0 the same minor: ADR-0022), and
 * a pending release (released heading, no tag yet) is compared against the previous release, and a commit after a
 * release's tag is refused until the next -SNAPSHOT is opened. A shallow clone fails,
 * because a released contract's tag may be missing. `-PcontractBaseline=none` skips the comparison on purpose and
 * says so, and is refused when the environment variable CI is true; the gates that read no git history (the version
 * gates and the Breaking subsection) still run.
 * It declares no outputs, so it runs every time: a new tag changes its answer without changing any file.
 */
@DisableCachingByDefault(because = "Reads git history and tags, which are not file inputs.")
abstract class CheckContractCompatibility @Inject constructor(
    private val archives: ArchiveOperations,
    private val files: FileSystemOperations,
) : ContractToolTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val bundle: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val openapi: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val changelog: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val severityLevels: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val vacuum: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val oasdiff: RegularFileProperty
    @get:Classpath abstract val gateClasspath: ConfigurableFileCollection
    @get:Nested abstract val launcher: Property<JavaLauncher>
    @get:Input @get:Optional abstract val baselineOverride: Property<String>
    @get:Input @get:Optional abstract val ci: Property<String>
    @get:Internal abstract val repository: DirectoryProperty

    /**
     * Runs the release gate (`CheckReleaseGate`): its first line of standard output is the decision, its second
     * the message. Standard error is kept apart, since a JVM may print notes there (JAVA_TOOL_OPTIONS).
     */
    private fun gate(vararg mode: String): Pair<String, String> {
        val (exit, lines, errors) = runGate(openapi.get().asFile.absolutePath, changelog.get().asFile.absolutePath, *mode)
        val decision = lines.firstOrNull().orEmpty()
        if (exit != 0 || !Regex("COMPARE \\S+|NONE|SKIPPED").matches(decision)) {
            throw GradleException("The contract release gate refuses (exit $exit):\n" +
                lines.joinToString("\n") + errors.let { if (it.isBlank()) "" else "\n$it" })
        }
        return decision to lines.drop(1).joinToString("\n")
    }

    /** Runs `CheckReleaseGate` with [args]; the result is its exit code, its standard output lines and its standard error. */
    private fun runGate(vararg args: String): Triple<Int, List<String>, String> {
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val result = execOps.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = gateClasspath
            mainClass = "ai.speckitty.charter.contracts.compat.CheckReleaseGate"
            args(*args)
            standardOutput = output
            errorOutput = errors
            isIgnoreExitValue = true
        }
        return Triple(result.exitValue, output.toString(Charsets.UTF_8).trim().lines(), errors.toString(Charsets.UTF_8))
    }

    /**
     * The bundle of the contract as released at [tag]: `git archive` of the tag, the check that its `info.version` is
     * the version the tag names, then the bundling every comparison uses.
     */
    private fun bundledRelease(repo: String, tag: String, work: File): File {
        val dir = File(work, tag)
        val tar = File(dir, "baseline.tar")
        dir.mkdirs()
        val (archiveCode, archiveOut) = run("git", "-C", repo, "archive", "--format=tar", "-o", tar.path,
            tag, "contracts/charter", "contracts/_shared")
        if (archiveCode != 0) throw GradleException("Cannot read the contract at $tag:\n$archiveOut")
        val tree = File(dir, "tree")
        files.copy { from(archives.tarTree(tar)); into(tree) }
        // The tag must name the contract it holds: a tag moved onto a later commit is not the release.
        val (versionExit, versionLines, versionErrors) =
            runGate("--baseline", File(tree, "contracts/charter/openapi.yaml").path, tag)
        if (versionExit != 0) {
            throw GradleException("The baseline $tag is not the release it names (exit $versionExit):\n" +
                versionLines.joinToString("\n") + versionErrors.let { if (it.isBlank()) "" else "\n$it" })
        }
        val bundled = File(dir, "openapi.yaml")
        bundle(vacuum.get().asFile, File(tree, "contracts/charter/openapi.yaml"), bundled)?.let {
            throw GradleException("Cannot bundle the contract at $tag:\n$it")
        }
        return bundled
    }

    /**
     * On a breaking bump (a major bump, or a minor bump while the major is 0), shows what the new version breaks
     * against the previous release, the newest of the earlier line: the oasdiff report, for information only. Nothing
     * here fails the build; a problem is logged and the check goes on.
     */
    private fun reportOnPreviousBreaking(repo: String, tagsFile: File, work: File) {
        val (exit, lines, _) = runGate("--previous-breaking", openapi.get().asFile.absolutePath,
            changelog.get().asFile.absolutePath, tagsFile.path)
        val first = lines.firstOrNull().orEmpty()
        if (exit != 0 || !first.startsWith("PREVIOUS_BREAKING ")) return
        val tag = first.removePrefix("PREVIOUS_BREAKING ")
        try {
            val previous = bundledRelease(repo, tag, work)
            val (code, output) = breaking(oasdiff.get().asFile, severityLevels.get().asFile, previous, bundle.get().asFile)
            val verdict = when (code) {
                0 -> "the new version breaks nothing"
                1 -> "the new version breaks:"
                else -> "oasdiff exited $code:"
            }
            logger.lifecycle("Information only (a major bump, or a minor bump of 0.x, may break; this does not fail " +
                "the build): compared with " +
                "$tag, $verdict" + if (output.isBlank()) "" else "\n$output")
        } catch (e: GradleException) {
            logger.warn("Could not compare the breaking bump with $tag (information only): ${e.message}")
        }
    }

    @TaskAction
    fun compare() {
        if (baselineOverride.orNull == "none") {
            if (ci.orNull.equals("true", ignoreCase = true)) {
                throw GradleException("-PcontractBaseline=none is refused when CI=true: CI must compare against the " +
                    "released baseline. Fetch the full history and tags (actions/checkout fetch-depth: 0) and drop " +
                    "the property.")
            }
            val (_, message) = gate("--no-git")
            logger.lifecycle("Compatibility check skipped on request (-PcontractBaseline=none): no baseline was " +
                "compared. $message")
            return
        } else if (baselineOverride.isPresent) {
            throw GradleException("-PcontractBaseline accepts only 'none', not '${baselineOverride.get()}'.")
        }
        val repo = repository.get().asFile.path
        val howToFix = "Fetch the full history and tags (actions/checkout fetch-depth: 0). Locally, when you " +
            "cannot deepen the clone, -PcontractBaseline=none skips the check on purpose; CI refuses it."
        val (shallowCode, shallow) = run("git", "-C", repo, "rev-parse", "--is-shallow-repository")
        if (shallowCode != 0) throw GradleException("Cannot read the git history of $repo:\n$shallow")
        if (shallow.trim() == "true") {
            throw GradleException("This is a shallow clone, so a released contract's tag may be missing. $howToFix")
        }
        // Only tags merged into HEAD: an older commit or a stale branch never sees a later release.
        val (tagCode, tagList) = run("git", "-C", repo, "tag", "--merged", "HEAD", "--list", "contract-charter-v*")
        if (tagCode != 0) throw GradleException("Cannot list the contract tags:\n$tagList")
        val work = temporaryDir
        files.delete { delete(work.listFiles()) }
        // The tags that point at HEAD itself tell the gate whether HEAD is the release commit.
        val (headCode, headTagList) = run("git", "-C", repo, "tag", "--points-at", "HEAD", "--list", "contract-charter-v*")
        if (headCode != 0) throw GradleException("Cannot list the contract tags at HEAD:\n$headTagList")
        val tagsFile = File(work, "merged-tags.txt")
        tagsFile.writeText(tagList)
        val headTagsFile = File(work, "head-tags.txt")
        headTagsFile.writeText(headTagList)
        val (decision, message) = gate("--git", tagsFile.path, headTagsFile.path)
        reportOnPreviousBreaking(repo, tagsFile, work)
        if (decision == "NONE") {
            logger.lifecycle(message)
            return
        }
        val baselineTag = decision.removePrefix("COMPARE ")
        val baseline = bundledRelease(repo, baselineTag, work)
        val (code, output) = breaking(oasdiff.get().asFile, severityLevels.get().asFile, baseline, bundle.get().asFile)
        if (code != 0) {
            throw GradleException("The charter contract breaks compatibility with $baselineTag ($message):\n$output")
        }
        logger.lifecycle("Compatible with $baselineTag ($message)." + if (output.isBlank()) "" else "\n$output")
    }
}

/**
 * Runs the closed-response-objects checker (`ai.speckitty.charter.contracts.closure`) over the bundle; its class
 * Javadoc is the authority on what closed means. The checker's output is the report; any violation fails the task.
 */
@CacheableTask
abstract class CheckClosedResponseObjects @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val bundle: RegularFileProperty
    @get:Classpath abstract val checkerClasspath: ConfigurableFileCollection
    @get:Nested abstract val launcher: Property<JavaLauncher>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val output = ByteArrayOutputStream()
        val result = exec.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = checkerClasspath
            mainClass = "ai.speckitty.charter.contracts.closure.CheckClosedResponseObjects"
            args(bundle.get().asFile.absolutePath)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        val text = output.toString(Charsets.UTF_8)
        report.get().asFile.writeText(text)
        if (result.exitValue != 0) {
            throw GradleException("The charter contract has open response schemas:\n$text")
        }
        logger.lifecycle(text.trim())
    }
}

/**
 * The publishing gate of a release tag (`ReleaseGate.releaseRefusal`): the tag `-PreleaseTag` names, `info.version`
 * and the top CHANGELOG heading must name one released version. `contract-release.yml` runs it before it assembles
 * anything; it is not part of `check`, because a commit on main is not a release.
 */
@DisableCachingByDefault(because = "A gate with no outputs: it runs each time it is asked.")
abstract class CheckReleaseTag @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val openapi: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val changelog: RegularFileProperty
    @get:Classpath abstract val gateClasspath: ConfigurableFileCollection
    @get:Nested abstract val launcher: Property<JavaLauncher>
    @get:Input @get:Optional abstract val tag: Property<String>

    @TaskAction
    fun check() {
        val releaseTag = tag.orNull
            ?: throw GradleException("Name the release tag: -PreleaseTag=contract-charter-v<version>")
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val result = exec.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = gateClasspath
            mainClass = "ai.speckitty.charter.contracts.compat.CheckReleaseGate"
            args("--release", openapi.get().asFile.absolutePath, changelog.get().asFile.absolutePath, releaseTag)
            standardOutput = output
            errorOutput = errors
            isIgnoreExitValue = true
        }
        val text = output.toString(Charsets.UTF_8).trim()
        if (result.exitValue != 0) {
            throw GradleException("The release tag $releaseTag is refused (exit ${result.exitValue}):\n$text" +
                errors.toString(Charsets.UTF_8).let { if (it.isBlank()) "" else "\n$it" })
        }
        logger.lifecycle(text)
    }
}

val charterContract = layout.projectDirectory.dir("charter")
val rulesetFile = layout.projectDirectory.file("lint/ruleset.yaml")
// Raises oasdiff's response-*-property-added to ERR: an addition to a closed response is breaking
// (DM 01M5986E711JT296MA6NDN91MG).
val severityLevelsFile = layout.projectDirectory.file("compat/severity-levels.txt")

val bundleContract = tasks.register<BundleContract>("bundleContract") {
    group = "contract"
    description = "Bundles contracts/charter/openapi.yaml into build/contracts/contract/charter-openapi.yaml and validates it."
    contractFiles.from(fileTree(layout.projectDirectory) {
        include("charter/**/*.yaml", "_shared/**/*.yaml")
        exclude("charter/scenarios/**")
    })
    openapi = charterContract.file("openapi.yaml")
    openapiPath = "contracts/charter/openapi.yaml"
    vacuum = vacuumExe
    oasdiff = oasdiffExe
    bundle = layout.buildDirectory.file("contract/charter-openapi.yaml")
    checksum = layout.buildDirectory.file("contract/charter-openapi.yaml.sha256")
}

/** The variant attribute value of the bundled contract, which no Java variant carries. */
val contractBundleCategory: Category = objects.named("charter-contract-bundle")

// The bundle `bundleContract` writes (and validates with oasdiff), published as a variant: :deployable packages it
// and serves it as GET /openapi.yaml (#209), so the service serves the build output, never a copy. The checksum
// travels with it for the deployable's tests. Consumers reach it through the variant, never through the task, which
// keeps the configuration cache valid.
configurations.consumable("contractBundle") {
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, contractBundleCategory) }
    outgoing.artifact(bundleContract.flatMap { it.bundle })
    outgoing.artifact(bundleContract.flatMap { it.checksum })
}

val lintContract = tasks.register<LintContract>("lintContract") {
    group = "verification"
    description = "Lints the bundled charter contract with the charter's ruleset (contracts/lint/ruleset.yaml)."
    ruleset = rulesetFile
    bundle = bundleContract.flatMap { it.bundle }
    vacuum = vacuumExe
    ruleCount = ruleIds(rulesetFile.asFile).size
    report = layout.buildDirectory.file("reports/lint/charter-openapi.txt")
}

val lintFixtures = tasks.register<LintFixtures>("lintFixtures") {
    group = "verification"
    description = "Checks that every lint rule fails its planted fixtures, and only it, and that the clean fixture passes."
    ruleset = rulesetFile
    fixtures.from(fileTree(layout.projectDirectory.dir("lint/fixtures")) { include("*.yaml") })
    vacuum = vacuumExe
    rules = ruleIds(rulesetFile.asFile)
    report = layout.buildDirectory.file("reports/lint/fixtures.txt")
}

val compatibilityFixtures = tasks.register<CompatibilityFixtures>("compatibilityFixtures") {
    group = "verification"
    description = "Checks that the breaking-change comparison fails each planted breaking pair and passes each compatible one."
    fixtures.from(fileTree(layout.projectDirectory.dir("compat/fixtures")) { include("*/*.yaml") })
    severityLevels = severityLevelsFile
    oasdiff = oasdiffExe
    report = layout.buildDirectory.file("reports/compat/fixtures.txt")
}

val checkContractCompatibility = tasks.register<CheckContractCompatibility>("checkContractCompatibility") {
    group = "verification"
    description = "Checks the bundle for breaking changes against the last released contract (see contracts/lint/README.md)."
    bundle = bundleContract.flatMap { it.bundle }
    openapi = charterContract.file("openapi.yaml")
    changelog = charterContract.file("CHANGELOG.md")
    severityLevels = severityLevelsFile
    vacuum = vacuumExe
    oasdiff = oasdiffExe
    gateClasspath.from(sourceSets.main.map { it.runtimeClasspath })
    launcher = javaToolchains.launcherFor(java.toolchain)
    baselineOverride = providers.gradleProperty("contractBaseline")
    ci = providers.environmentVariable("CI")
    repository = rootProject.layout.projectDirectory
}

val lintCleanFixture = layout.projectDirectory.file("lint/fixtures/clean.yaml")
tasks.test {
    // The checker must pass the lint ruleset's own positive control too.
    inputs.file(lintCleanFixture).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("lintCleanFixture")
    systemProperty("charter.lintCleanFixture", lintCleanFixture.asFile.absolutePath)
    // ActivationConfigMirrorTest compares the canonical ActivationConfig JSON Schema with its OpenAPI mirror (#125).
    val offeringSchemas = layout.projectDirectory.dir("offering/schemas")
    val charterSchemas = layout.projectDirectory.dir("charter/schemas")
    inputs.dir(offeringSchemas).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("offeringSchemas")
    inputs.dir(charterSchemas).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("charterSchemas")
    systemProperty("charter.offeringSchemas", offeringSchemas.asFile.absolutePath)
    systemProperty("charter.charterSchemas", charterSchemas.asFile.absolutePath)
}

val checkClosedResponseObjects = tasks.register<CheckClosedResponseObjects>("checkClosedResponseObjects") {
    group = "verification"
    description = "Checks that every response schema of the bundle is closed (the Java checker in src/main/java)."
    bundle = bundleContract.flatMap { it.bundle }
    checkerClasspath.from(sourceSets.main.map { it.runtimeClasspath })
    launcher = javaToolchains.launcherFor(java.toolchain)
    report = layout.buildDirectory.file("reports/closure/charter-openapi.txt")
}

tasks.register<CheckReleaseTag>("checkReleaseTag") {
    group = "contract"
    description = "Checks that -PreleaseTag, info.version and the top CHANGELOG heading name one released version."
    openapi = charterContract.file("openapi.yaml")
    changelog = charterContract.file("CHANGELOG.md")
    gateClasspath.from(sourceSets.main.map { it.runtimeClasspath })
    launcher = javaToolchains.launcherFor(java.toolchain)
    tag = providers.gradleProperty("releaseTag")
}

tasks.named("check") {
    dependsOn(bundleContract, lintContract, lintFixtures, checkClosedResponseObjects, compatibilityFixtures,
        checkContractCompatibility)
}
