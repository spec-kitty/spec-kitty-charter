/**
 * The publishing gate of a service release (ADR-0046 decisions 1 to 3 and 7): whether a pushed `service-v*` tag may be
 * published from the commit it names. The facts come from that commit: the build's `version` (gradle.properties), the
 * bundled contract's `info.version`, and whether that contract's release tag is merged into it. The tag's commit being
 * on `main`, and still named by the tag when publishing, are checked by the release workflow, which has the remote.
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

    /** What the gate answers. */
    sealed interface Verdict

    /**
     * The tag may be published.
     *
     * @property version the release version, the tag without `service-v`
     * @property prerelease whether the version has a pre-release suffix, which makes a GitHub pre-release
     * @property contract the bundled contract's version
     * @property contractReleased whether that contract is released and its tag merged into the tagged commit
     */
    data class Release(
        val version: String,
        val prerelease: Boolean,
        val contract: String,
        val contractReleased: Boolean,
    ) : Verdict {
        /** The release notes' line about the contract this release implements (ADR-0046 decision 3). */
        fun contractNote(): String =
            if (contractReleased) {
                "It implements the charter contract $contract (released as contract-charter-v$contract)."
            } else {
                "It implements the charter contract $contract, which is not released yet."
            }
    }

    /** The tag may not be published; [reason] names the cause and the fix. */
    data class Refusal(val reason: String) : Verdict

    /**
     * Judges a tag.
     *
     * @param tag the pushed tag
     * @param buildVersion `version` in gradle.properties at the tagged commit
     * @param contractVersion `info.version` of contracts/charter/openapi.yaml at the tagged commit
     * @param contractTagMerged whether `contract-charter-v<contractVersion>` is an ancestor of the tagged commit
     */
    fun check(tag: String, buildVersion: String, contractVersion: String, contractTagMerged: Boolean): Verdict {
        val match = TAG.matchEntire(tag)
            ?: return Refusal(
                "The tag '$tag' is not a service release tag service-vX.Y.Z[-pre]: a pre-release uses only letters," +
                    " digits and dots (rc.1, not rc-1), and build metadata (+...) is not allowed (ADR-0046 decision 1)",
            )
        val version = match.groupValues[1]
        if (version == PLACEHOLDER) {
            return Refusal("The tag '$tag' names the placeholder version $PLACEHOLDER, which core refuses to pin")
        }
        if (version.endsWith(SNAPSHOT)) {
            return Refusal("The tag '$tag' names a -SNAPSHOT, which is never published: release a version without it")
        }
        if (version != buildVersion) {
            return Refusal(
                "The tag '$tag' names $version, but the build's version in gradle.properties is $buildVersion:" +
                    " release the commit whose version is $version (ADR-0046 decision 2)",
            )
        }
        val prerelease = match.groupValues[2].isNotEmpty()
        val contractReleased = !contractVersion.endsWith(SNAPSHOT) && contractTagMerged
        if (!prerelease && !contractReleased) {
            val why = if (contractVersion.endsWith(SNAPSHOT)) {
                "the bundled contract $contractVersion is unreleased"
            } else {
                "the contract tag contract-charter-v$contractVersion is not merged into the tagged commit"
            }
            return Refusal(
                "The final release '$tag' needs a released contract, but $why: release the contract first, or tag" +
                    " a pre-release (ADR-0046 decision 3)",
            )
        }
        return Release(version, prerelease, contractVersion, contractReleased)
    }
}
