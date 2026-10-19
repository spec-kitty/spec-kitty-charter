package ai.speckitty.charter.contracts.compat;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.contracts.compat.ReleaseGate.Decision;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReleaseGateTest {

    private static final String SNAPSHOT_ONLY = "# Changelog\n\nIntro.\n\n## 1.0.0-SNAPSHOT\n\n### Added\n\n- x\n";

    private static String changelog(String... sections) {
        return "# Changelog\n\nAll notable changes.\n\n" + String.join("\n", sections);
    }

    @Nested
    class VersionGate {

        @Test
        void theUnreleasedFirstVersionHasNoBaseline() {
            Decision decision = ReleaseGate.decide("1.0.0-SNAPSHOT", SNAPSHOT_ONLY, Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("records no released version");
        }

        @Test
        void anInfoVersionThatIsNotTheTopHeadingIsRefused() {
            Decision decision = ReleaseGate.decide("1.1.0-SNAPSHOT", SNAPSHOT_ONLY, Set.of());
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message())
                    .contains("info.version 1.1.0-SNAPSHOT")
                    .contains("top heading 1.0.0-SNAPSHOT");
        }

        @Test
        void aReleasedInfoVersionUnderASnapshotHeadingIsRefused() {
            assertThat(ReleaseGate.decide("1.0.0", SNAPSHOT_ONLY, Set.of())).isInstanceOf(Decision.Refused.class);
        }

        @Test
        void anInfoVersionThatIsNoVersionIsRefused() {
            assertThat(ReleaseGate.decide("1.0", SNAPSHOT_ONLY, Set.of()).message())
                    .contains("info.version '1.0' is not X.Y.Z or X.Y.Z-SNAPSHOT");
        }

        @Test
        void aTopHeadingThatIsNoVersionIsRefused() {
            Decision decision = ReleaseGate.decide("1.0.0", changelog("## Unreleased\n", "## 1.0.0\n"), Set.of());
            assertThat(decision.message())
                    .contains("The top heading 'Unreleased'")
                    .contains("is not a version");
        }

        @Test
        void aChangelogWithoutHeadingsIsRefused() {
            assertThat(ReleaseGate.decide("1.0.0", "# Changelog\n", Set.of()).message())
                    .contains("has no version heading");
        }

        @Test
        void headingStylesAreTolerated() {
            for (String heading : new String[] {"## 1.1.0", "## [1.1.0] - 2026-10-19", "## v1.1.0", "##  1.1.0  "}) {
                Decision decision = ReleaseGate.decide(
                        "1.1.0",
                        changelog(heading + "\n", "### Fixed\n", "## 1.0.0\n"),
                        Set.of("contract-charter-v1.0.0"));
                assertThat(decision)
                        .as(heading)
                        .isEqualTo(new Decision.Compare(
                                "contract-charter-v1.0.0", "pending release 1.1.0: compared against 1.0.0"));
            }
        }

        @Test
        void aThirdLevelHeadingIsNotAVersionHeading() {
            Decision decision = ReleaseGate.decide("1.0.0-SNAPSHOT", changelog("### 9.9.9\n", SNAPSHOT_ONLY), Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
        }
    }

    @Nested
    class PendingRelease {

        @Test
        void theFirstReleaseBeforeItsTagHasNoBaselineAndSaysPending() {
            Decision decision = ReleaseGate.decide("1.0.0", changelog("## 1.0.0\n"), Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("pending release 1.0.0");
        }

        @Test
        void aLaterReleaseBeforeItsTagIsComparedWithThePreviousRelease() {
            Decision decision = ReleaseGate.decide(
                    "1.1.0", changelog("## 1.1.0\n", "## 1.0.0\n"), Set.of("contract-charter-v1.0.0"));
            assertThat(decision)
                    .isEqualTo(new Decision.Compare(
                            "contract-charter-v1.0.0", "pending release 1.1.0: compared against 1.0.0"));
        }

        @Test
        void aTaggedReleaseIsComparedWithItself() {
            Decision decision = ReleaseGate.decide(
                    "1.1.0",
                    changelog("## 1.1.0\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0", "contract-charter-v1.1.0"));
            assertThat(decision).isEqualTo(new Decision.Compare("contract-charter-v1.1.0", "compared against 1.1.0"));
        }

        @Test
        void aSnapshotAfterAReleaseIsComparedWithThatRelease() {
            Decision decision = ReleaseGate.decide(
                    "1.2.0-SNAPSHOT",
                    changelog("## 1.2.0-SNAPSHOT\n", "## 1.1.0\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0", "contract-charter-v1.1.0"));
            assertThat(decision).isEqualTo(new Decision.Compare("contract-charter-v1.1.0", "compared against 1.1.0"));
        }
    }

    @Nested
    class Tags {

        @Test
        void aRecordedReleaseWhoseTagIsNotMergedIsRefused() {
            Decision decision = ReleaseGate.decide(
                    "1.2.0-SNAPSHOT",
                    changelog("## 1.2.0-SNAPSHOT\n", "## 1.1.0\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("contract-charter-v1.1.0 is not merged into HEAD");
        }

        @Test
        void aMergedTagTheChangelogDoesNotRecordIsRefused() {
            Decision decision = ReleaseGate.decide("1.0.0-SNAPSHOT", SNAPSHOT_ONLY, Set.of("contract-charter-v0.9.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("[contract-charter-v0.9.0]").contains("does not record");
        }
    }

    @Nested
    class SameMajor {

        private static final String TWO = "## 2.0.0-SNAPSHOT\n\n### Breaking\n\n- dropped x\n";

        @Test
        void aNewMajorWithOnlyOlderMajorTagsHasNoBaseline() {
            Decision decision = ReleaseGate.decide(
                    "2.0.0-SNAPSHOT", changelog(TWO, "## 1.1.0\n"), Set.of("contract-charter-v1.1.0"));
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("no released 2.x version");
        }

        @Test
        void theNewestReleaseOfTheSameMajorIsTheBaseline() {
            Decision decision = ReleaseGate.decide(
                    "2.1.0-SNAPSHOT",
                    changelog("## 2.1.0-SNAPSHOT\n", "## 2.0.0\n\n### Breaking\n", "## 1.1.0\n"),
                    Set.of("contract-charter-v1.1.0", "contract-charter-v2.0.0"));
            assertThat(decision).isEqualTo(new Decision.Compare("contract-charter-v2.0.0", "compared against 2.0.0"));
        }

        @Test
        void aMajorBumpWithoutABreakingSubsectionIsRefused() {
            Decision decision = ReleaseGate.decide(
                    "2.0.0-SNAPSHOT",
                    changelog("## 2.0.0-SNAPSHOT\n\n### Changed\n", "## 1.1.0\n\n### Breaking\n"),
                    Set.of("contract-charter-v1.1.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("raises the major version from 1.1.0 to 2.0.0-SNAPSHOT");
        }

        @Test
        void aPendingMajorReleaseNeedsItsBreakingSubsectionToo() {
            Decision refused = ReleaseGate.decide(
                    "2.0.0", changelog("## 2.0.0\n", "## 1.1.0\n"), Set.of("contract-charter-v1.1.0"));
            assertThat(refused).isInstanceOf(Decision.Refused.class);
            Decision passes = ReleaseGate.decide(
                    "2.0.0", changelog("## 2.0.0\n\n### Breaking\n", "## 1.1.0\n"), Set.of("contract-charter-v1.1.0"));
            assertThat(passes.message()).contains("pending release 2.0.0").contains("no released 2.x version");
        }
    }

    @Test
    void withoutGitOnlyTheChangelogGatesRun() {
        assertThat(ReleaseGate.decideWithoutGit("1.0.0-SNAPSHOT", SNAPSHOT_ONLY))
                .isInstanceOf(Decision.Skipped.class);
        assertThat(ReleaseGate.decideWithoutGit("1.1.0-SNAPSHOT", SNAPSHOT_ONLY))
                .isInstanceOf(Decision.Refused.class);
    }
}
