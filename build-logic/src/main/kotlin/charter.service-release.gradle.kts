// The publishing gate of a service release (ADR-0046), applied by the root project:
//   ./gradlew checkServiceReleaseTag -PreleaseTag=service-v<version>
// It judges the tag against the checked-out commit (ServiceReleaseGate): the tag's form, the build's version and,
// for a final release, a released and merged contract. It fails naming the cause, or writes
// build/service-release/gate.properties (version, prerelease, contract, contractReleased, contractNote) for the
// release workflow. That the commit is on main, and that the tag still names it when publishing, are the workflow's
// checks: they need the remote. scripts/release/rehearse-service-release.sh rehearses every refusal.

val contractVersion: Provider<String> =
    providers.fileContents(layout.projectDirectory.file("contracts/charter/openapi.yaml")).asText.map { text ->
        Regex("""(?m)^info:\n(?:[ ]+.*\n)*?[ ]{2}version:[ ]*(\S+)""").find(text)?.groupValues?.get(1)
            ?: throw GradleException("contracts/charter/openapi.yaml has no info.version")
    }

// Whether contract-charter-v<info.version> is an ancestor of HEAD; a tag that does not exist is not.
val contractTagMerged: Provider<Boolean> = contractVersion.flatMap { version ->
    providers.exec {
        commandLine("git", "-C", layout.projectDirectory.asFile.absolutePath, "merge-base", "--is-ancestor",
            "refs/tags/contract-charter-v$version", "HEAD")
        isIgnoreExitValue = true
    }.result.map { it.exitValue == 0 }
}

tasks.register("checkServiceReleaseTag") {
    group = "verification"
    description = "Judges -PreleaseTag=service-v<version> against this commit and writes build/service-release/gate.properties."
    val tag = providers.gradleProperty("releaseTag")
    val buildVersion = project.version.toString()
    val contract = contractVersion
    val merged = contractTagMerged
    val gate = layout.buildDirectory.file("service-release/gate.properties")
    outputs.file(gate)
    outputs.upToDateWhen { false }
    doLast {
        val pushed = tag.orNull ?: throw GradleException("Name the tag to judge: -PreleaseTag=service-v<version>")
        when (val verdict = ServiceReleaseGate.check(pushed, buildVersion, contract.get(), merged.get())) {
            is ServiceReleaseGate.Refusal -> throw GradleException("The release tag $pushed is refused: ${verdict.reason}")
            is ServiceReleaseGate.Release -> {
                val file = gate.get().asFile
                file.parentFile.mkdirs()
                file.writeText(
                    listOf(
                        "version=${verdict.version}",
                        "prerelease=${verdict.prerelease}",
                        "contract=${verdict.contract}",
                        "contractReleased=${verdict.contractReleased}",
                        "contractNote=${verdict.contractNote()}",
                    ).joinToString("\n", postfix = "\n"),
                )
                logger.lifecycle("The release tag $pushed may be published: ${file.readText().trim().replace("\n", ", ")}")
            }
        }
    }
}
