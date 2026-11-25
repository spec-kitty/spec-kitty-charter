/**
 * The publishing gate of a service release (ADR-0046 decisions 1 to 3 and 7): whether a pushed `service-v*` tag may be
 * published from the commit it names. The facts come from that commit: the build's `version` (gradle.properties), and
 * the two contracts the release bundles, each with whether its release tag is merged into it. The tag's commit being on
 * `main`, the contract releases being published with their assets, and the tag still naming the commit when publishing
 * are checked by the release workflow, which has the remote.
 *
 * Every release, a pre-release too, bundles released contracts (#650): the charter contract as
 * `contract-charter-v<info.version>`, and the packs contract as `contract-packs-v<version>`, where an unchanged packs
 * contract is the release it is unchanged since. A final release bundles final contract releases, never a release
 * candidate (operator ruling R1, 2026-11-16). ADR-0046 decisions 3 and 7 as amended 2026-11-25 hold the rules.
 */
object ServiceReleaseGate {

    /**
     * Core's tag rule (spec-kitty `kernel/service_pin.py`, `_TAG_RE`), anchored: a pre-release identifier holds only
     * letters, digits and dots, and build metadata is not allowed.
     */
    private val TAG = Regex("""service-v([0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?)""")

    /** Core refuses this version as a pin placeholder. */
    private const val PLACEHOLDER = "0.0.0"

    private const val SNAPSHOT = "-SNAPSHOT"

    /**
     * A contract as the tagged commit bundles it.
     *
     * @property version its version at the commit: `info.version` of the charter contract, the top heading of
     *   `contracts/packs/CHANGELOG.md` for the packs contract
     * @property tagMerged whether the release tag of [version] is an ancestor of the commit
     * @property unchangedSince the version of the highest release merged into the commit whose contract files the
     *   commit holds unchanged, or null; consulted for the packs contract only, whose version is reopened as a
     *   `-SNAPSHOT` right after each release
     */
    data class Bundled(val version: String, val tagMerged: Boolean, val unchangedSince: String? = null)

    /** What the gate answers. */
    sealed interface Verdict

    /**
     * The tag may be published.
     *
     * @property version the release version, the tag without `service-v`
     * @property prerelease whether the version has a pre-release suffix, which makes a GitHub pre-release
     * @property contract the released charter contract it bundles, published as `contract-charter-v<contract>`
     * @property packsContract the released packs contract it bundles, published as `contract-packs-v<packsContract>`
     */
    data class Release(
        val version: String,
        val prerelease: Boolean,
        val contract: String,
        val packsContract: String,
    ) : Verdict {
        /** The release notes' line about the contracts this release implements (ADR-0046 decision 3). */
        fun contractNote(): String =
            "It implements ${named("charter", contract)} and ${named("packs", packsContract)}."

        private fun named(line: String, released: String): String {
            val candidate = if (released.contains('-')) "release candidate " else ""
            return "the $line contract $candidate$released (contract-$line-v$released)"
        }
    }

    /** The tag may not be published; [reason] names the cause and the fix. */
    data class Refusal(val reason: String) : Verdict

    /**
     * Judges a tag.
     *
     * @param tag the pushed tag
     * @param buildVersion `version` in gradle.properties at the tagged commit
     * @param contract the charter contract at the tagged commit, `contracts/charter/openapi.yaml`
     * @param packsContract the packs contract at the tagged commit, `contracts/packs/`
     */
    fun check(tag: String, buildVersion: String, contract: Bundled, packsContract: Bundled): Verdict {
        val match = TAG.matchEntire(tag)
            ?: return Refusal(
                "The tag '$tag' is not a service release tag service-vX.Y.Z[-pre]: a pre-release uses only letters," +
                    " digits and dots (rc.1, not rc-1), and build metadata (+...) is not allowed (ADR-0046 decision 1)",
            )
        val version = match.groupValues[1]
        if (version == PLACEHOLDER) {
            return Refusal("The tag '$tag' names the placeholder version $PLACEHOLDER, which core refuses to pin")
        }
        if (version.endsWith(SNAPSHOT, ignoreCase = true)) {
            return Refusal("The tag '$tag' names a -SNAPSHOT, which is never published: release a version without it")
        }
        if (version != buildVersion) {
            return Refusal(
                "The tag '$tag' names $version, but the build's version in gradle.properties is $buildVersion:" +
                    " release the commit whose version is $version (ADR-0046 decision 2)",
            )
        }
        val prerelease = match.groupValues[2].isNotEmpty()
        val charter = releaseOf(contract, unchangedCounts = false) ?: return unreleased(tag, "charter", contract)
        candidate(tag, prerelease, "charter", charter)?.let { return it }
        val packs = releaseOf(packsContract, unchangedCounts = true) ?: return unreleased(tag, "packs", packsContract)
        candidate(tag, prerelease, "packs", packs)?.let { return it }
        return Release(version, prerelease, charter, packs)
    }

    /** The release a bundled contract is: its own version once tagged and merged, else the release it is unchanged since. */
    private fun releaseOf(bundled: Bundled, unchangedCounts: Boolean): String? {
        val snapshot = bundled.version.endsWith(SNAPSHOT, ignoreCase = true)
        return when {
            !snapshot && bundled.tagMerged -> bundled.version
            snapshot && unchangedCounts -> bundled.unchangedSince
            else -> null
        }
    }

    private fun unreleased(tag: String, line: String, bundled: Bundled): Refusal {
        val why = when {
            !bundled.version.endsWith(SNAPSHOT, ignoreCase = true) ->
                "the contract tag contract-$line-v${bundled.version} is not merged into the tagged commit"
            line == "packs" ->
                "the bundled packs contract ${bundled.version} is unreleased: it changed since its last release, or was" +
                    " never released, or the history to tell is missing (fetch the tags and the full history)"
            else -> "the bundled $line contract ${bundled.version} is unreleased"
        }
        val fix = if (line == "packs") "release the packs contract first" else "release the contract first"
        return Refusal(
            "The release '$tag' needs a released $line contract, but $why: $fix (contract-$line-v<version>), as every" +
                " service release, a pre-release too, bundles released contracts (#650)",
        )
    }

    /** A final release over a contract release candidate is refused (operator ruling R1, 2026-11-16). */
    private fun candidate(tag: String, prerelease: Boolean, line: String, release: String): Refusal? {
        if (prerelease || !release.contains('-')) return null
        val name = if (line == "packs") "packs contract" else "contract"
        return Refusal(
            "The final release '$tag' needs a final $name release, but the bundled $line contract $release is a" +
                " release candidate: release the final $name first, or tag a pre-release (ADR-0046 decision 3, as amended" +
                " 2026-11-25)",
        )
    }
}
