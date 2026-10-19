package ai.speckitty.charter.contracts.compat;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.contracts.compat.ReleaseGate.Decision;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReleaseGateTest {

    private static final String SNAPSHOT_ONLY = "# Changelog\n\nIntro.\n\n## 1.0.0-SNAPSHOT\n\n### Added\n\n- x\n";

    /** The gate with HEAD at no release tag, which is every case but the release commit itself. */
    private static Decision decide(String infoVersion, String changelog, Set<String> mergedTags) {
        return ReleaseGate.decide(infoVersion, changelog, mergedTags, Set.of());
    }

    private static String changelog(String... sections) {
        return "# Changelog\n\nAll notable changes.\n\n" + String.join("\n", sections);
    }

    @Nested
    class VersionGate {

        @Test
        void theUnreleasedFirstVersionHasNoBaseline() {
            Decision decision = decide("1.0.0-SNAPSHOT", SNAPSHOT_ONLY, Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("records no released version");
        }

        @Test
        void anInfoVersionThatIsNotTheTopHeadingIsRefused() {
            Decision decision = decide("1.1.0-SNAPSHOT", SNAPSHOT_ONLY, Set.of());
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message())
                    .contains("info.version 1.1.0-SNAPSHOT")
                    .contains("top heading 1.0.0-SNAPSHOT");
        }

        @Test
        void aReleasedInfoVersionUnderASnapshotHeadingIsRefused() {
            assertThat(decide("1.0.0", SNAPSHOT_ONLY, Set.of())).isInstanceOf(Decision.Refused.class);
        }

        @Test
        void anInfoVersionThatIsNoVersionIsRefused() {
            assertThat(decide("1.0", SNAPSHOT_ONLY, Set.of()).message())
                    .contains("info.version '1.0' is not X.Y.Z or X.Y.Z-SNAPSHOT");
        }

        @Test
        void aTopHeadingThatIsNoVersionIsRefused() {
            Decision decision = decide("1.0.0", changelog("## Unreleased\n", "## 1.0.0\n"), Set.of());
            assertThat(decision.message())
                    .contains("The top heading 'Unreleased'")
                    .contains("is not a version");
        }

        @Test
        void aChangelogWithoutHeadingsIsRefused() {
            assertThat(decide("1.0.0", "# Changelog\n", Set.of()).message()).contains("has no version heading");
        }

        @Test
        void headingStylesAreTolerated() {
            for (String heading : new String[] {"## 1.1.0", "## [1.1.0] - 2026-10-19", "## v1.1.0", "##  1.1.0  "}) {
                Decision decision = decide(
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
            Decision decision = decide("1.0.0-SNAPSHOT", changelog("### 9.9.9\n", SNAPSHOT_ONLY), Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
        }
    }

    @Nested
    class PendingRelease {

        @Test
        void theFirstReleaseBeforeItsTagHasNoBaselineAndSaysPending() {
            Decision decision = decide("1.0.0", changelog("## 1.0.0\n"), Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("pending release 1.0.0");
        }

        @Test
        void aLaterReleaseBeforeItsTagIsComparedWithThePreviousRelease() {
            Decision decision =
                    decide("1.1.0", changelog("## 1.1.0\n", "## 1.0.0\n"), Set.of("contract-charter-v1.0.0"));
            assertThat(decision)
                    .isEqualTo(new Decision.Compare(
                            "contract-charter-v1.0.0", "pending release 1.1.0: compared against 1.0.0"));
        }

        @Test
        void theCommitOfTheReleaseTagIsComparedWithItself() {
            Decision decision = ReleaseGate.decide(
                    "1.1.0",
                    changelog("## 1.1.0\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0", "contract-charter-v1.1.0"),
                    Set.of("contract-charter-v1.1.0"));
            assertThat(decision).isEqualTo(new Decision.Compare("contract-charter-v1.1.0", "compared against 1.1.0"));
        }

        @Test
        void aCommitAfterTheReleaseTagUnderTheReleasedHeadingIsRefused() {
            Decision decision = decide(
                    "1.1.0",
                    changelog("## 1.1.0\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0", "contract-charter-v1.1.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message())
                    .contains("contract-charter-v1.1.0 is merged into HEAD")
                    .contains("HEAD is not that release")
                    .contains("open the next -SNAPSHOT");
        }

        @Test
        void aTagAtHeadForAnotherVersionDoesNotExcuseTheTopRelease() {
            Decision decision = ReleaseGate.decide(
                    "1.1.0",
                    changelog("## 1.1.0\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0", "contract-charter-v1.1.0"),
                    Set.of("contract-charter-v1.0.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
        }

        @Test
        void aSnapshotAfterAReleaseIsComparedWithThatRelease() {
            Decision decision = decide(
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
            Decision decision = decide(
                    "1.2.0-SNAPSHOT",
                    changelog("## 1.2.0-SNAPSHOT\n", "## 1.1.0\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("contract-charter-v1.1.0 is not merged into HEAD");
        }

        @Test
        void aMergedTagTheChangelogDoesNotRecordIsRefused() {
            Decision decision = decide("1.0.0-SNAPSHOT", SNAPSHOT_ONLY, Set.of("contract-charter-v0.9.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("[contract-charter-v0.9.0]").contains("does not record");
        }
    }

    @Nested
    class SameMajor {

        private static final String TWO = "## 2.0.0-SNAPSHOT\n\n### Breaking\n\n- dropped x\n";

        @Test
        void aNewMajorWithOnlyOlderMajorTagsHasNoBaseline() {
            Decision decision =
                    decide("2.0.0-SNAPSHOT", changelog(TWO, "## 1.1.0\n"), Set.of("contract-charter-v1.1.0"));
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("no released 2.x version");
        }

        @Test
        void theNewestReleaseOfTheSameMajorIsTheBaseline() {
            Decision decision = decide(
                    "2.1.0-SNAPSHOT",
                    changelog("## 2.1.0-SNAPSHOT\n", "## 2.0.0\n\n### Breaking\n", "## 1.1.0\n"),
                    Set.of("contract-charter-v1.1.0", "contract-charter-v2.0.0"));
            assertThat(decision).isEqualTo(new Decision.Compare("contract-charter-v2.0.0", "compared against 2.0.0"));
        }

        @Test
        void aMajorBumpWithoutABreakingSubsectionIsRefused() {
            Decision decision = decide(
                    "2.0.0-SNAPSHOT",
                    changelog("## 2.0.0-SNAPSHOT\n\n### Changed\n", "## 1.1.0\n\n### Breaking\n"),
                    Set.of("contract-charter-v1.1.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("raises the major version from 1.1.0 to 2.0.0-SNAPSHOT");
        }

        @Test
        void aPendingMajorReleaseNeedsItsBreakingSubsectionToo() {
            Decision refused =
                    decide("2.0.0", changelog("## 2.0.0\n", "## 1.1.0\n"), Set.of("contract-charter-v1.1.0"));
            assertThat(refused).isInstanceOf(Decision.Refused.class);
            Decision passes = decide(
                    "2.0.0", changelog("## 2.0.0\n\n### Breaking\n", "## 1.1.0\n"), Set.of("contract-charter-v1.1.0"));
            assertThat(passes.message()).contains("pending release 2.0.0").contains("no released 2.x version");
        }
    }

    @Nested
    class VersionOrder {

        @Test
        void headingsThatAreNotDescendingAreRefused() {
            Decision decision = decide("1.3.0", changelog("## 1.3.0\n", "## 1.1.0\n", "## 1.2.0\n"), Set.of());
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("not in descending order").contains("1.2.0 is listed after 1.1.0");
        }

        @Test
        void aSnapshotTopBelowAReleasedHeadingIsRefused() {
            Decision decision = decide(
                    "1.1.5-SNAPSHOT",
                    changelog("## 1.1.5-SNAPSHOT\n", "## 1.2.0\n", "## 1.1.0\n"),
                    Set.of("contract-charter-v1.2.0", "contract-charter-v1.1.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message())
                    .contains("top heading 1.1.5-SNAPSHOT is not above the released 1.2.0")
                    .contains("-SNAPSHOT");
        }

        @Test
        void aFirstSnapshotBelowAReleasedMajorIsRefused() {
            Decision decision = decide(
                    "1.0.0-SNAPSHOT",
                    changelog("## 1.0.0-SNAPSHOT\n", "## 2.0.0\n\n### Breaking\n"),
                    Set.of("contract-charter-v2.0.0"));
            assertThat(decision.message()).contains("top heading 1.0.0-SNAPSHOT is not above the released 2.0.0");
        }

        @Test
        void theSnapshotOfAReleasedVersionIsNotAboveIt() {
            Decision decision = decide("1.2.0-SNAPSHOT", changelog("## 1.2.0-SNAPSHOT\n", "## 1.2.0\n"), Set.of());
            assertThat(decision.message()).contains("not above the released 1.2.0");
        }

        @Test
        void aDuplicateHeadingIsRefused() {
            Decision decision = decide("1.1.0", changelog("## 1.1.0\n", "## [1.0.0]\n", "## v1.0.0\n"), Set.of());
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("lists 1.0.0 twice");
        }

        @Test
        void orderIsComparedByNumberNotByText() {
            Decision decision = decide(
                    "1.10.0",
                    changelog("## 1.10.0\n", "## 1.9.0\n", "## 1.2.0\n"),
                    Set.of("contract-charter-v1.9.0", "contract-charter-v1.2.0"));
            assertThat(decision)
                    .isEqualTo(new Decision.Compare(
                            "contract-charter-v1.9.0", "pending release 1.10.0: compared against 1.9.0"));
        }

        @Test
        void theOrderRulesRunWithoutGitToo() {
            assertThat(ReleaseGate.decideWithoutGit("1.0.0-SNAPSHOT", changelog("## 1.0.0-SNAPSHOT\n", "## 2.0.0\n")))
                    .isInstanceOf(Decision.Refused.class);
        }
    }

    @Nested
    class Fences {

        @Test
        void headingsInsideAFencedCodeBlockAreIgnored() {
            String fenced = "```markdown\n## 9.9.9\n\n### Breaking\n```\n";
            Decision decision = decide("1.0.0-SNAPSHOT", changelog(fenced, SNAPSHOT_ONLY), Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
        }

        @Test
        void aTildeFenceAndALongerFenceAreIgnoredToo() {
            String tilde = "~~~\n## 9.9.9\n~~~\n";
            String longer = "````\n```\n## 8.8.8\n```\n````\n";
            Decision decision = decide("1.0.0-SNAPSHOT", changelog(tilde, longer, SNAPSHOT_ONLY), Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
        }

        @Test
        void aBreakingHeadingInsideAFenceDoesNotCount() {
            Decision decision = decide(
                    "2.0.0-SNAPSHOT",
                    changelog("## 2.0.0-SNAPSHOT\n\n```\n### Breaking\n```\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0"));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message()).contains("no ### Breaking");
        }

        @Test
        void aHeadingAfterTheFenceClosesCountsAgain() {
            Decision decision = decide(
                    "1.1.0-SNAPSHOT",
                    changelog("```\n## 9.9.9\n```\n", "## 1.1.0-SNAPSHOT\n", "## 1.0.0\n"),
                    Set.of("contract-charter-v1.0.0"));
            assertThat(decision).isInstanceOf(Decision.Compare.class);
        }
    }

    @Nested
    class MalformedVersions {

        @Test
        void aVersionNumberThatOverflowsIsARefusalNotAnException() {
            Decision heading = decide("99999999999.0.0", changelog("## 99999999999.0.0\n"), Set.of());
            assertThat(heading).isInstanceOf(Decision.Refused.class);
            assertThat(heading.message()).contains("99999999999.0.0").contains("above 2147483647");
            Decision lower =
                    decide("1.0.0-SNAPSHOT", changelog("## 1.0.0-SNAPSHOT\n", "## 1.0.99999999999\n"), Set.of());
            assertThat(lower.message()).contains("1.0.99999999999").contains("above 2147483647");
        }

        @Test
        void anInfoVersionThatOverflowsIsRefused() {
            assertThat(decide("1.99999999999.0", SNAPSHOT_ONLY, Set.of()).message())
                    .contains("info.version '1.99999999999.0'")
                    .contains("above 2147483647");
        }

        @Test
        void leadingZerosAreNotSemVer() {
            Decision heading = decide("01.2.0", changelog("## 01.2.0\n"), Set.of());
            assertThat(heading.message()).contains("info.version '01.2.0'").contains("leading zero");
            Decision lower = decide("1.3.0", changelog("## 1.3.0\n", "## 1.02.0\n"), Set.of());
            assertThat(lower).isInstanceOf(Decision.Refused.class);
            assertThat(lower.message()).contains("heading '1.02.0'").contains("leading zero");
        }

        @Test
        void zeroItselfIsFine() {
            assertThat(decide("0.0.0-SNAPSHOT", changelog("## 0.0.0-SNAPSHOT\n"), Set.of()))
                    .isInstanceOf(Decision.NoBaseline.class);
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
