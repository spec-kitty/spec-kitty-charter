import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

// The git probes and the packs contract's file list that two gates share: the service release gate
// (charter.service-release) and the packs contract's CHANGELOG check (contracts/build.gradle.kts, #663). One form of
// each, so the two agree. checkContractCompatibility keeps its own probes, run inside its task action.

/** The packs contract's files, as its CHANGELOG defines them. The README and the CHANGELOG are not the contract. */
val PACKS_CONTRACT_FILES = listOf(
    "contracts/packs/file-kinds.yaml", "contracts/packs/schemas", "contracts/packs/conformance",
)

/** Whether `refs/tags/<tag>` is an ancestor of HEAD; a tag that does not exist is not. */
fun Project.tagMerged(tag: Provider<String>): Provider<Boolean> {
    val providers = providers
    val root = rootDir.absolutePath
    return tag.flatMap { name ->
        providers.exec {
            commandLine("git", "-C", root, "merge-base", "--is-ancestor", "refs/tags/$name", "HEAD")
            isIgnoreExitValue = true
        }.result.map { it.exitValue == 0 }
    }
}

/**
 * Whether the files of [paths] at HEAD differ from those at `refs/tags/<tag>`; a tag that does not exist differs. Ask
 * it only for a tag found merged.
 */
fun Project.changedSince(tag: Provider<String>, paths: List<String>): Provider<Boolean> {
    val providers = providers
    val root = rootDir.absolutePath
    return tag.flatMap { name -> differs(providers, root, name, paths) }
}

/**
 * The tags matching [pattern] that are merged into HEAD (`git tag --merged HEAD --list <pattern>`), in git's order. A
 * git failure fails the build: an empty list would let the tag rules pass unchecked.
 */
fun Project.tagsMerged(pattern: String): Provider<List<String>> =
    git(providers, rootDir.absolutePath, "tag", "--merged", "HEAD", "--list", pattern)
        .map { out -> out.lines().map(String::trim).filter(String::isNotEmpty) }

/** Those of [tags] at which the files of [paths] differ from HEAD's: [changedSince] asked of each. */
fun Project.tagsChangedSince(tags: Provider<List<String>>, paths: List<String>): Provider<List<String>> {
    val providers = providers
    val root = rootDir.absolutePath
    return tags.flatMap { names ->
        names.fold(providers.provider { emptyList<String>() }) { found, name ->
            found.zip(differs(providers, root, name, paths)) { list, changed -> if (changed) list + name else list }
        }
    }
}

/**
 * Whether this checkout is a shallow clone, in which a release tag or its history may be missing. A git failure (not
 * a git repository) fails the build.
 */
fun Project.shallowClone(): Provider<Boolean> =
    git(providers, rootDir.absolutePath, "rev-parse", "--is-shallow-repository").map { it.trim() != "false" }

/** The standard output of `git -C <root> <args>`, failing the build with git's message when git exits non-zero. */
private fun git(providers: ProviderFactory, root: String, vararg args: String): Provider<String> {
    val run = providers.exec {
        commandLine(listOf("git", "-C", root) + args)
        isIgnoreExitValue = true
    }
    return run.result.zip(run.standardOutput.asText) { result, out -> result.exitValue to out }
        .zip(run.standardError.asText) { (code, out), err ->
            if (code != 0) {
                throw GradleException("Cannot read the git history of $root (git ${args.joinToString(" ")}):\n$err")
            }
            out
        }
}

private fun differs(providers: ProviderFactory, root: String, tag: String, paths: List<String>): Provider<Boolean> =
    providers.exec {
        commandLine(listOf("git", "-C", root, "diff", "--quiet", "refs/tags/$tag", "HEAD", "--") + paths)
        isIgnoreExitValue = true
    }.result.map { it.exitValue != 0 }
