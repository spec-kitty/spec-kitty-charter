// The publishing gate of a service release (ADR-0046), applied by the root project:
//   ./gradlew checkServiceReleaseTag -PreleaseTag=service-v<version>
// It judges the tag against the checked-out commit (ServiceReleaseGate): the tag's form, the build's version and, for
// every release, a pre-release too (#650), the two contracts it bundles released and merged: the charter contract as
// contract-charter-v<info.version>, and the packs contract as contract-packs-v<top heading of contracts/packs/CHANGELOG.md>
// or, while that heading is a -SNAPSHOT and no packs contract file (file-kinds.yaml, schemas/, conformance/) changed
// since the highest merged contract-packs-v* release, as that release. It fails naming the cause, or writes
// build/service-release/gate.properties (version, prerelease, contract, packsContract, contractNote) for the release
// workflow. That the commit is on main, that both contract releases are published with their assets, and that the tag
// still names the commit when publishing are the workflow's checks: they need the remote.
// scripts/release/rehearse-service-release.sh rehearses every refusal.

val root = layout.projectDirectory.asFile.absolutePath

/** Whether `refs/tags/<tag>` is an ancestor of HEAD; a tag that does not exist is not. */
fun tagMerged(tag: Provider<String>): Provider<Boolean> = tag.flatMap { name ->
    providers.exec {
        commandLine("git", "-C", root, "merge-base", "--is-ancestor", "refs/tags/$name", "HEAD")
        isIgnoreExitValue = true
    }.result.map { it.exitValue == 0 }
}

val contractVersion: Provider<String> = contractInfoVersion

val packsChangelog = layout.projectDirectory.file("contracts/packs/CHANGELOG.md")

// The packs contract's version: the top heading of its CHANGELOG (contracts/packs/README.md).
val packsVersion: Provider<String> = providers.fileContents(packsChangelog).asText.map { text ->
    Regex("""(?m)^## (\S+)""").find(text)?.groupValues?.get(1)
        ?: throw GradleException("contracts/packs/CHANGELOG.md has no version heading (## X.Y.Z)")
}

// The highest contract-packs-v* release merged into HEAD, or "" when there is none: by version, not by commit
// distance, and a final release above its candidates (versionsort.suffix), so a final tag cut on its last candidate's
// commit is the one taken.
val newestPacksTag: Provider<String> = providers.exec {
    commandLine(
        "git", "-C", root, "-c", "versionsort.suffix=-", "tag", "--merged", "HEAD", "--list", "contract-packs-v*",
        "--sort=-v:refname",
    )
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.lineSequence().firstOrNull { line -> line.isNotBlank() }?.trim().orEmpty() }

// The release of that tag when the packs contract's files at HEAD, as its CHANGELOG defines them (file-kinds.yaml,
// schemas/, conformance/), are those of the tag, or "". The README and the CHANGELOG are not the contract.
val packsUnchangedSince: Provider<String> = newestPacksTag.flatMap { tag ->
    providers.exec {
        commandLine(
            "git", "-C", root, "diff", "--quiet", if (tag.isEmpty()) "HEAD" else "refs/tags/$tag", "HEAD", "--",
            "contracts/packs/file-kinds.yaml", "contracts/packs/schemas", "contracts/packs/conformance",
        )
        isIgnoreExitValue = true
    }.result.map { if (tag.isNotEmpty() && it.exitValue == 0) tag.removePrefix("contract-packs-v") else "" }
}

tasks.register("checkServiceReleaseTag") {
    group = "verification"
    description = "Judges -PreleaseTag=service-v<version> against this commit and writes build/service-release/gate.properties."
    val tag = providers.gradleProperty("releaseTag")
    val buildVersion = project.version.toString()
    val contract = contractVersion
    val contractMerged = tagMerged(contractVersion.map { "contract-charter-v$it" })
    val packs = packsVersion
    val packsMerged = tagMerged(packsVersion.map { "contract-packs-v$it" })
    val packsUnchanged = packsUnchangedSince
    val gate = layout.buildDirectory.file("service-release/gate.properties")
    outputs.file(gate)
    outputs.upToDateWhen { false }
    doLast {
        val pushed = tag.orNull ?: throw GradleException("Name the tag to judge: -PreleaseTag=service-v<version>")
        val verdict = ServiceReleaseGate.check(
            pushed,
            buildVersion,
            ServiceReleaseGate.Bundled(contract.get(), contractMerged.get()),
            ServiceReleaseGate.Bundled(packs.get(), packsMerged.get(), packsUnchanged.get().ifEmpty { null }),
        )
        when (verdict) {
            is ServiceReleaseGate.Refusal -> throw GradleException("The release tag $pushed is refused: ${verdict.reason}")
            is ServiceReleaseGate.Release -> {
                val file = gate.get().asFile
                file.parentFile.mkdirs()
                file.writeText(
                    listOf(
                        "version=${verdict.version}",
                        "prerelease=${verdict.prerelease}",
                        "contract=${verdict.contract}",
                        "packsContract=${verdict.packsContract}",
                        "contractNote=${verdict.contractNote()}",
                    ).joinToString("\n", postfix = "\n"),
                )
                logger.lifecycle("The release tag $pushed may be published: ${file.readText().trim().replace("\n", ", ")}")
            }
        }
    }
}
