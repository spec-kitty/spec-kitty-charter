import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The publishing gate of a service release (ADR-0046 decisions 1 to 3 and 7): every release, a pre-release too, ships
 * only when the charter contract and the packs contract it bundles are released (#650).
 */
class ServiceReleaseGateTest {

    private val charter = ServiceReleaseGate.Bundled("0.1.0", tagMerged = true)
    private val packs = ServiceReleaseGate.Bundled("0.1.0", tagMerged = true)

    private fun check(
        tag: String,
        build: String = tag.removePrefix("service-v"),
        contract: ServiceReleaseGate.Bundled = charter,
        packsContract: ServiceReleaseGate.Bundled = packs,
    ) = ServiceReleaseGate.check(tag, build, contract, packsContract)

    private fun refusal(
        tag: String,
        build: String = "0.2.0",
        contract: ServiceReleaseGate.Bundled = charter,
        packsContract: ServiceReleaseGate.Bundled = packs,
    ) = (check(tag, build, contract, packsContract) as ServiceReleaseGate.Refusal).reason

    @Test
    fun aFinalReleaseOfTheBuildVersionWithBothContractsReleasedPasses() {
        assertThat(check("service-v0.2.0"))
            .isEqualTo(ServiceReleaseGate.Release("0.2.0", prerelease = false, contract = "0.1.0", packsContract = "0.1.0"))
    }

    @Test
    fun aPreReleaseOfAnUnreleasedContractIsRefused() {
        assertThat(refusal("service-v0.2.0-rc.1", build = "0.2.0-rc.1", contract = ServiceReleaseGate.Bundled("0.2.0-SNAPSHOT", false)))
            .contains("service-v0.2.0-rc.1", "0.2.0-SNAPSHOT", "unreleased", "release the contract first")
        assertThat(refusal("service-v0.2.0-rc.1", build = "0.2.0-rc.1", contract = ServiceReleaseGate.Bundled("0.2.0-rc.1", false)))
            .contains("contract-charter-v0.2.0-rc.1", "not merged")
    }

    @Test
    fun aSnapshotContractIsUnreleasedEvenWhenATagOfThatNameIsMerged() {
        assertThat(refusal("service-v0.2.0", contract = ServiceReleaseGate.Bundled("0.2.0-SNAPSHOT", tagMerged = true)))
            .contains("0.2.0-SNAPSHOT", "unreleased")
    }

    @Test
    fun aPreReleaseWithReleasedContractsPasses() {
        assertThat(check("service-v1.0.0-beta.2", contract = ServiceReleaseGate.Bundled("1.0.0", true)))
            .isEqualTo(ServiceReleaseGate.Release("1.0.0-beta.2", prerelease = true, contract = "1.0.0", packsContract = "0.1.0"))
    }

    @Test
    fun tagsCoreRefusesAreRefusedHereNamingTheForm() {
        for (tag in listOf("service-v1.0", "service-v1.0.0-rc-1", "service-v1.0.0+build.5", "v1.0.0", "service-1.0.0",
            "contract-charter-v1.0.0", "service-v01.0.0x", " service-v1.0.0", "service-v1.0.0-")) {
            assertThat(refusal(tag, build = tag.removePrefix("service-v")))
                .`as`(tag)
                .contains("service-vX.Y.Z[-pre]")
        }
    }

    @Test
    fun aSnapshotIsNeverPublishedEvenWhenItIsTheBuildVersion() {
        assertThat(refusal("service-v0.1.0-SNAPSHOT", build = "0.1.0-SNAPSHOT")).contains("SNAPSHOT", "never published")
        assertThat(refusal("service-v0.1.0-snapshot", build = "0.1.0-snapshot")).contains("never published")
    }

    @Test
    fun thePlaceholderVersionIsRefused() {
        assertThat(refusal("service-v0.0.0", build = "0.0.0")).contains("placeholder")
    }

    @Test
    fun aTagThatIsNotTheBuildVersionIsRefusedNamingBoth() {
        assertThat(refusal("service-v0.2.0", build = "0.2.0-SNAPSHOT"))
            .contains("0.2.0", "0.2.0-SNAPSHOT", "gradle.properties")
        assertThat(refusal("service-v0.2.0", build = "0.3.0")).contains("0.3.0")
    }

    @Test
    fun aFinalReleaseOfAnUnreleasedContractIsRefused() {
        assertThat(refusal("service-v0.2.0", contract = ServiceReleaseGate.Bundled("0.2.0-SNAPSHOT", false)))
            .contains("0.2.0-SNAPSHOT", "unreleased")
        assertThat(refusal("service-v0.2.0", contract = ServiceReleaseGate.Bundled("0.2.0", false)))
            .contains("contract-charter-v0.2.0", "not merged")
    }

    @Test
    fun aFinalReleaseOfAContractReleaseCandidateIsRefused() {
        assertThat(refusal("service-v0.2.0", contract = ServiceReleaseGate.Bundled("0.2.0-rc.1", true)))
            .contains("needs a final contract release", "0.2.0-rc.1", "release candidate")
        assertThat(refusal("service-v0.2.0", contract = ServiceReleaseGate.Bundled("0.2.0-rc.1", false)))
            .contains("contract-charter-v0.2.0-rc.1", "not merged")
    }

    @Test
    fun aPreReleaseMayBundleContractReleaseCandidatesAndSaysSo() {
        val release = check(
            "service-v0.2.0-rc.1",
            contract = ServiceReleaseGate.Bundled("0.2.0-rc.1", true),
            packsContract = ServiceReleaseGate.Bundled("0.1.0-rc.2", true),
        )
        assertThat(release)
            .isEqualTo(ServiceReleaseGate.Release("0.2.0-rc.1", prerelease = true, contract = "0.2.0-rc.1", packsContract = "0.1.0-rc.2"))
        assertThat((release as ServiceReleaseGate.Release).contractNote())
            .isEqualTo(
                "It implements the charter contract release candidate 0.2.0-rc.1 (contract-charter-v0.2.0-rc.1) and" +
                    " the packs contract release candidate 0.1.0-rc.2 (contract-packs-v0.1.0-rc.2).",
            )
    }

    @Test
    fun theReleaseNotesLineNamesBothContractReleases() {
        assertThat(ServiceReleaseGate.Release("0.2.0", false, "0.2.0", "0.1.0").contractNote())
            .isEqualTo(
                "It implements the charter contract 0.2.0 (contract-charter-v0.2.0) and the packs contract 0.1.0" +
                    " (contract-packs-v0.1.0).",
            )
    }

    @Test
    fun aPreReleaseOfAnUnreleasedPacksContractIsRefused() {
        assertThat(refusal("service-v0.2.0-rc.1", build = "0.2.0-rc.1", packsContract = ServiceReleaseGate.Bundled("0.1.0-SNAPSHOT", false)))
            .contains("service-v0.2.0-rc.1", "packs contract 0.1.0-SNAPSHOT", "changed since its last release", "release the packs contract first")
    }

    @Test
    fun aPacksContractReleaseWhoseTagIsNotMergedIsRefusedEvenWhenItIsUnchanged() {
        assertThat(refusal("service-v0.2.0", packsContract = ServiceReleaseGate.Bundled("0.1.0-rc.3", false)))
            .contains("contract-packs-v0.1.0-rc.3", "not merged")
        assertThat(refusal("service-v0.2.0", packsContract = ServiceReleaseGate.Bundled("0.2.0", false, unchangedSince = "0.1.0")))
            .contains("contract-packs-v0.2.0", "not merged")
    }

    @Test
    fun aSnapshotPacksContractIsUnreleasedEvenWhenATagOfThatNameIsMerged() {
        assertThat(refusal("service-v0.2.0", packsContract = ServiceReleaseGate.Bundled("0.2.0-SNAPSHOT", tagMerged = true)))
            .contains("packs contract 0.2.0-SNAPSHOT", "unreleased")
    }

    @Test
    fun anUnchangedPacksContractIsTheReleaseItIsUnchangedSince() {
        // The packs contract's version is its CHANGELOG's top heading, reopened as -SNAPSHOT right after each release;
        // while no file but the CHANGELOG changed since the newest merged contract-packs-v* tag, the bundled packs
        // contract is that release.
        assertThat(check("service-v0.2.0", packsContract = ServiceReleaseGate.Bundled("0.2.0-SNAPSHOT", false, unchangedSince = "0.1.0")))
            .isEqualTo(ServiceReleaseGate.Release("0.2.0", prerelease = false, contract = "0.1.0", packsContract = "0.1.0"))
    }

    @Test
    fun theCharterContractHasNoUnchangedExemption() {
        // Its info.version is served by GET /openapi.yaml and named in the release notes: a -SNAPSHOT is never pinnable.
        assertThat(refusal("service-v0.2.0", contract = ServiceReleaseGate.Bundled("0.2.0-SNAPSHOT", false, unchangedSince = "0.1.0")))
            .contains("0.2.0-SNAPSHOT", "unreleased")
    }

    @Test
    fun aFinalReleaseOfAPacksContractReleaseCandidateIsRefused() {
        assertThat(refusal("service-v0.2.0", packsContract = ServiceReleaseGate.Bundled("0.1.0-rc.2", true)))
            .contains("needs a final packs contract release", "0.1.0-rc.2", "release candidate")
        assertThat(refusal("service-v0.2.0", packsContract = ServiceReleaseGate.Bundled("0.1.0-SNAPSHOT", false, unchangedSince = "0.1.0-rc.2")))
            .contains("needs a final packs contract release", "0.1.0-rc.2")
    }

    @Test
    fun theCharterContractIsJudgedBeforeThePacksContract() {
        assertThat(
            refusal(
                "service-v0.2.0",
                contract = ServiceReleaseGate.Bundled("0.2.0-SNAPSHOT", false),
                packsContract = ServiceReleaseGate.Bundled("0.1.0-SNAPSHOT", false),
            ),
        ).contains("charter contract 0.2.0-SNAPSHOT").doesNotContain("packs contract")
    }

    @Test
    fun aReleasedContractWhoseFilesChangedSinceItsTagIsRefused() {
        // Reviewer probe P1: both contracts tagged and merged, then edited without reopening -SNAPSHOT. The version
        // still names the release, but the bundled bytes are no longer those it published.
        val charterEdited = ServiceReleaseGate.Bundled("0.9.0", tagMerged = true, changedSinceTag = true)
        val packsEdited = ServiceReleaseGate.Bundled("0.9.0", tagMerged = true, changedSinceTag = true)
        assertThat(refusal("service-v0.2.0", contract = charterEdited))
            .contains("charter contract 0.9.0", "changed since contract-charter-v0.9.0", "-SNAPSHOT")
        assertThat(refusal("service-v0.2.0-rc.1", build = "0.2.0-rc.1", packsContract = packsEdited))
            .contains("packs contract 0.9.0", "changed since contract-packs-v0.9.0", "-SNAPSHOT")
    }

    @Test
    fun aSnapshotHasNoTagToDifferFromSoOnlyItsUnchangedReleaseCounts() {
        // The build diffs against contract-packs-v<version>, which no -SNAPSHOT has: the diff reports a change, and
        // the release the packs contract is unchanged since was diffed against its own tag already.
        val reopened = ServiceReleaseGate.Bundled("0.10.0-SNAPSHOT", false, unchangedSince = "0.9.0", changedSinceTag = true)
        assertThat(check("service-v0.2.0", packsContract = reopened))
            .isEqualTo(ServiceReleaseGate.Release("0.2.0", prerelease = false, contract = "0.1.0", packsContract = "0.9.0"))
    }
}
