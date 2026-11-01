import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The publishing gate of a service release (ADR-0046 decisions 1 to 3 and 7). */
class ServiceReleaseGateTest {

    private fun refusal(tag: String, build: String = "0.2.0", contract: String = "0.1.0", merged: Boolean = true) =
        (ServiceReleaseGate.check(tag, build, contract, merged) as ServiceReleaseGate.Refusal).reason

    @Test
    fun aFinalReleaseOfTheBuildVersionWithAReleasedContractPasses() {
        assertThat(ServiceReleaseGate.check("service-v0.2.0", "0.2.0", "0.1.0", contractTagMerged = true))
            .isEqualTo(ServiceReleaseGate.Release("0.2.0", prerelease = false, contract = "0.1.0", contractReleased = true))
    }

    @Test
    fun aPreReleaseMayBundleAnUnreleasedContractAndSaysSo() {
        assertThat(ServiceReleaseGate.check("service-v0.2.0-rc.1", "0.2.0-rc.1", "0.2.0-SNAPSHOT", contractTagMerged = false))
            .isEqualTo(
                ServiceReleaseGate.Release(
                    "0.2.0-rc.1", prerelease = true, contract = "0.2.0-SNAPSHOT", contractReleased = false,
                ),
            )
    }

    @Test
    fun aPreReleaseWithAReleasedContractSaysSoToo() {
        assertThat(ServiceReleaseGate.check("service-v1.0.0-beta.2", "1.0.0-beta.2", "1.0.0", contractTagMerged = true))
            .isEqualTo(ServiceReleaseGate.Release("1.0.0-beta.2", prerelease = true, contract = "1.0.0", contractReleased = true))
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
        assertThat(refusal("service-v0.2.0", contract = "0.2.0-SNAPSHOT", merged = false))
            .contains("0.2.0-SNAPSHOT", "unreleased")
        assertThat(refusal("service-v0.2.0", contract = "0.2.0", merged = false))
            .contains("contract-charter-v0.2.0", "not merged")
    }

    @Test
    fun theReleaseNotesLineNamesTheContractAndWhetherItIsReleased() {
        assertThat(ServiceReleaseGate.Release("0.2.0", false, "0.1.0", true).contractNote())
            .isEqualTo("It implements the charter contract 0.1.0 (released as contract-charter-v0.1.0).")
        assertThat(ServiceReleaseGate.Release("0.2.0-rc.1", true, "0.2.0-SNAPSHOT", false).contractNote())
            .isEqualTo("It implements the charter contract 0.2.0-SNAPSHOT, which is not released yet.")
    }
}
