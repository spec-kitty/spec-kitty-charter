package ai.speckitty.charter.contracts.compat;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.contracts.compat.ReleaseGate.Decision;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReleaseGateTest {

    private static final Set<String> NO_TAGS = Set.of();

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
            assertThat(decision.message()).contains("records no final release");
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
                    .contains("info.version '1.0' is not X.Y.Z, X.Y.Z-<pre> or X.Y.Z-SNAPSHOT");
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
            assertThat(decision.message()).contains("no final 2.x release");
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
            assertThat(passes.message()).contains("pending release 2.0.0").contains("no final 2.x release");
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

    @Nested
    class PreviousMajor {

        private static final String BREAKING_TWO = "## 2.0.0-SNAPSHOT\n\n### Breaking\n\n- dropped x\n";

        @Test
        void aMajorBumpNamesTheNewestReleaseOfTheEarlierMajor() {
            assertThat(ReleaseGate.previousBreakingBaseline(
                            "2.0.0-SNAPSHOT",
                            changelog(BREAKING_TWO, "## 1.1.0\n", "## 1.0.0\n"),
                            Set.of("contract-charter-v1.1.0", "contract-charter-v1.0.0")))
                    .contains("contract-charter-v1.1.0");
        }

        @Test
        void aPendingMajorReleaseNamesItToo() {
            assertThat(ReleaseGate.previousBreakingBaseline(
                            "2.0.0",
                            changelog("## 2.0.0\n\n### Breaking\n", "## 1.1.0\n"),
                            Set.of("contract-charter-v1.1.0")))
                    .contains("contract-charter-v1.1.0");
        }

        @Test
        void aSameMajorVersionNamesNothing() {
            assertThat(ReleaseGate.previousBreakingBaseline(
                            "1.2.0-SNAPSHOT",
                            changelog("## 1.2.0-SNAPSHOT\n", "## 1.1.0\n"),
                            Set.of("contract-charter-v1.1.0")))
                    .isEmpty();
        }

        @Test
        void aMajorBumpWithoutABreakingSubsectionNamesNothing() {
            assertThat(ReleaseGate.previousBreakingBaseline(
                            "2.0.0-SNAPSHOT",
                            changelog("## 2.0.0-SNAPSHOT\n", "## 1.1.0\n"),
                            Set.of("contract-charter-v1.1.0")))
                    .isEmpty();
        }

        @Test
        void aReleaseWhoseTagIsNotMergedNamesNothing() {
            assertThat(ReleaseGate.previousBreakingBaseline(
                            "2.0.0-SNAPSHOT", changelog(BREAKING_TWO, "## 1.1.0\n"), Set.of()))
                    .isEmpty();
        }

        @Test
        void theFirstVersionNamesNothing() {
            assertThat(ReleaseGate.previousBreakingBaseline("1.0.0-SNAPSHOT", SNAPSHOT_ONLY, Set.of()))
                    .isEmpty();
        }
    }

    @Nested
    class ZeroMajor {

        private static final String TAGS_01 = "contract-charter-v0.1.0";

        @Test
        void theFirstZeroSnapshotHasNoBaseline() {
            Decision decision = decide("0.1.0-SNAPSHOT", changelog("## 0.1.0-SNAPSHOT\n\n### Added\n"), Set.of());
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("records no final release");
        }

        @Test
        void aZeroPatchIsComparedWithTheReleaseOfTheSameMinor() {
            Decision decision =
                    decide("0.1.1-SNAPSHOT", changelog("## 0.1.1-SNAPSHOT\n", "## 0.1.0\n"), Set.of(TAGS_01));
            assertThat(decision).isEqualTo(new Decision.Compare(TAGS_01, "compared against 0.1.0"));
        }

        @Test
        void aPendingZeroPatchReleaseIsComparedWithThePreviousPatch() {
            Decision decision = decide("0.1.1", changelog("## 0.1.1\n", "## 0.1.0\n"), Set.of(TAGS_01));
            assertThat(decision)
                    .isEqualTo(new Decision.Compare(TAGS_01, "pending release 0.1.1: compared against 0.1.0"));
        }

        @Test
        void aZeroPatchIsNotComparedWithAnOlderMinor() {
            Decision decision = decide(
                    "0.2.1-SNAPSHOT",
                    changelog("## 0.2.1-SNAPSHOT\n", "## 0.2.0\n\n### Breaking\n", "## 0.1.0\n"),
                    Set.of(TAGS_01, "contract-charter-v0.2.0"));
            assertThat(decision).isEqualTo(new Decision.Compare("contract-charter-v0.2.0", "compared against 0.2.0"));
        }

        @Test
        void aZeroSnapshotWithOnlyAnOlderMinorReleasedHasNoBaseline() {
            Decision decision = decide(
                    "0.2.0-SNAPSHOT",
                    changelog("## 0.2.0-SNAPSHOT\n\n### Breaking\n\n- dropped x\n", "## 0.1.0\n"),
                    Set.of(TAGS_01));
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message())
                    .contains("no final 0.2.x release")
                    .contains("a new minor version of 0.x is not compared with an older one");
        }

        @Test
        void aZeroMinorBumpWithoutABreakingSubsectionIsRefused() {
            Decision decision = decide(
                    "0.2.0-SNAPSHOT", changelog("## 0.2.0-SNAPSHOT\n\n### Changed\n", "## 0.1.0\n"), Set.of(TAGS_01));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message())
                    .contains("raises the minor version of 0.x from 0.1.0 to 0.2.0-SNAPSHOT")
                    .contains("no ### Breaking");
            assertThat(ReleaseGate.decideWithoutGit("0.2.0-SNAPSHOT", changelog("## 0.2.0-SNAPSHOT\n", "## 0.1.0\n")))
                    .isInstanceOf(Decision.Refused.class);
        }

        @Test
        void aPendingZeroMinorReleaseNeedsItsBreakingSubsectionToo() {
            assertThat(decide("0.2.0", changelog("## 0.2.0\n", "## 0.1.0\n"), Set.of(TAGS_01)))
                    .isInstanceOf(Decision.Refused.class);
            Decision passes = decide("0.2.0", changelog("## 0.2.0\n\n### Breaking\n", "## 0.1.0\n"), Set.of(TAGS_01));
            assertThat(passes).isInstanceOf(Decision.NoBaseline.class);
            assertThat(passes.message()).contains("pending release 0.2.0").contains("no final 0.2.x release");
        }

        @Test
        void aZeroMinorBumpWithABreakingSubsectionNamesTheNewestReleaseOfThePreviousMinor() {
            String log = changelog("## 0.2.0-SNAPSHOT\n\n### Breaking\n", "## 0.1.3\n", "## 0.1.0\n");
            assertThat(ReleaseGate.previousBreakingBaseline(
                            "0.2.0-SNAPSHOT", log, Set.of(TAGS_01, "contract-charter-v0.1.3")))
                    .contains("contract-charter-v0.1.3");
            assertThat(ReleaseGate.previousBreakingBaseline("0.2.0-SNAPSHOT", log, Set.of(TAGS_01)))
                    .isEmpty();
        }

        @Test
        void aZeroPatchNamesNothing() {
            assertThat(ReleaseGate.previousBreakingBaseline(
                            "0.1.1-SNAPSHOT", changelog("## 0.1.1-SNAPSHOT\n", "## 0.1.0\n"), Set.of(TAGS_01)))
                    .isEmpty();
        }

        @Test
        void theStepFromZeroToOneIsAMajorBumpAndNeedsABreakingSubsection() {
            Set<String> tags = Set.of("contract-charter-v0.3.0");
            Decision refused = decide("1.0.0-SNAPSHOT", changelog("## 1.0.0-SNAPSHOT\n", "## 0.3.0\n"), tags);
            assertThat(refused).isInstanceOf(Decision.Refused.class);
            assertThat(refused.message()).contains("raises the major version from 0.3.0 to 1.0.0-SNAPSHOT");
            String log = changelog("## 1.0.0-SNAPSHOT\n\n### Breaking\n", "## 0.3.0\n");
            Decision passes = decide("1.0.0-SNAPSHOT", log, tags);
            assertThat(passes).isInstanceOf(Decision.NoBaseline.class);
            assertThat(passes.message()).contains("no final 1.x release");
            assertThat(ReleaseGate.previousBreakingBaseline("1.0.0-SNAPSHOT", log, tags))
                    .contains("contract-charter-v0.3.0");
        }

        @Test
        void aOneXMinorBumpStillNeedsNoBreakingSubsectionAndNamesNothing() {
            String log = changelog("## 1.1.0-SNAPSHOT\n", "## 1.0.0\n\n### Breaking\n", "## 0.3.0\n");
            Set<String> tags = Set.of("contract-charter-v0.3.0", "contract-charter-v1.0.0");
            assertThat(decide("1.1.0-SNAPSHOT", log, tags))
                    .isEqualTo(new Decision.Compare("contract-charter-v1.0.0", "compared against 1.0.0"));
            assertThat(ReleaseGate.previousBreakingBaseline("1.1.0-SNAPSHOT", log, tags))
                    .isEmpty();
        }
    }

    @Nested
    class BaselineContract {

        @Test
        void aBaselineWhoseInfoVersionIsTheTagsVersionPasses() {
            assertThat(ReleaseGate.baselineRefusal("contract-charter-v1.0.0", "1.0.0"))
                    .isEmpty();
        }

        @Test
        void aTagMovedOntoASnapshotCommitIsRefused() {
            Optional<Decision.Refused> refused =
                    ReleaseGate.baselineRefusal("contract-charter-v1.0.0", "1.1.0-SNAPSHOT");
            assertThat(refused).isPresent();
            assertThat(refused.get().message())
                    .contains("contract-charter-v1.0.0 names 1.0.0")
                    .contains("info.version is 1.1.0-SNAPSHOT");
        }

        @Test
        void aTagWithoutAVersionIsRefused() {
            assertThat(ReleaseGate.baselineRefusal("contract-charter-vnext", "1.0.0"))
                    .isPresent();
        }
    }

    @Nested
    class PublishedRelease {

        private static final String RELEASED = "## 0.1.0\n\n### Added\n\n- x\n";

        @Test
        void aTagThatNamesTheReleasedTopHeadingAndInfoVersionPasses() {
            assertThat(ReleaseGate.releaseRefusal("contract-charter-v0.1.0", "0.1.0", RELEASED))
                    .isEmpty();
        }

        @Test
        void aTagThatNamesAnotherVersionIsRefused() {
            Optional<Decision.Refused> refused =
                    ReleaseGate.releaseRefusal("contract-charter-v0.2.0", "0.1.0", RELEASED);
            assertThat(refused).isPresent();
            assertThat(refused.get().message())
                    .contains("contract-charter-v0.2.0 names 0.2.0")
                    .contains("info.version is 0.1.0");
        }

        @Test
        void aSnapshotIsNeverPublished() {
            String snapshot = "## 0.1.0-SNAPSHOT\n\n### Added\n\n- x\n";
            assertThat(ReleaseGate.releaseRefusal("contract-charter-v0.1.0", "0.1.0-SNAPSHOT", snapshot))
                    .isPresent();
            Optional<Decision.Refused> refused =
                    ReleaseGate.releaseRefusal("contract-charter-v0.1.0-SNAPSHOT", "0.1.0-SNAPSHOT", snapshot);
            assertThat(refused).isPresent();
            assertThat(refused.get().message()).contains("is not a release (X.Y.Z) or a release candidate");
        }

        @Test
        void anInfoVersionTheTopHeadingDoesNotMatchIsRefused() {
            assertThat(ReleaseGate.releaseRefusal("contract-charter-v0.1.0", "0.1.0", "## 0.2.0\n\n## 0.1.0\n"))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .isEqualTo("info.version 0.1.0 of contracts/charter/openapi.yaml does not equal the top"
                                    + " heading 0.2.0 of contracts/charter/CHANGELOG.md; bump both together."));
        }

        @Test
        void aTagWithoutThePrefixIsRefused() {
            assertThat(ReleaseGate.releaseRefusal("0.1.0", "0.1.0", RELEASED)).isPresent();
            assertThat(ReleaseGate.releaseRefusal("contract-charter-vnext", "0.1.0", RELEASED))
                    .isPresent();
        }
    }

    @Nested
    class PacksContractRelease {

        private static final String RELEASED = "## 0.1.0\n\n### Added\n\n- x\n";

        @Test
        void aTagThatNamesTheReleasedTopHeadingPasses() {
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.0", RELEASED))
                    .isEmpty();
        }

        @Test
        void aTagThatNamesAnotherVersionIsRefusedNamingThePacksChangelog() {
            Optional<Decision.Refused> refused = ReleaseGate.packsReleaseRefusal("contract-packs-v0.2.0", RELEASED);
            assertThat(refused).isPresent();
            assertThat(refused.get().message())
                    .contains("contract-packs-v0.2.0 names 0.2.0")
                    .contains("top heading of contracts/packs/CHANGELOG.md at that commit is 0.1.0");
        }

        @Test
        void aSnapshotIsNeverPublished() {
            String snapshot = "## 0.1.0-SNAPSHOT\n";
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.0", snapshot))
                    .hasValueSatisfying(r -> assertThat(r.message()).contains("is 0.1.0-SNAPSHOT"));
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.0-SNAPSHOT", snapshot))
                    .hasValueSatisfying(
                            r -> assertThat(r.message()).contains("is not a release (X.Y.Z) or a release candidate"));
        }

        @Test
        void aCharterTagOrABareVersionIsNotAPacksTag() {
            assertThat(ReleaseGate.packsReleaseRefusal("contract-charter-v0.1.0", RELEASED))
                    .hasValueSatisfying(r -> assertThat(r.message()).contains("not a packs contract release tag"));
            assertThat(ReleaseGate.packsReleaseRefusal("0.1.0", RELEASED))
                    .hasValueSatisfying(r -> assertThat(r.message()).contains("not a packs contract release tag"));
        }

        @Test
        void aTagThatNamesNoVersionSaysWhyWithoutBlamingASnapshot() {
            for (String tag :
                    new String[] {"contract-packs-vnext", "contract-packs-v01.0.0", "contract-packs-v0.1.0+m"}) {
                assertThat(ReleaseGate.packsReleaseRefusal(tag, RELEASED))
                        .as(tag)
                        .hasValueSatisfying(r -> assertThat(r.message())
                                .contains("is not a release (X.Y.Z) or a release candidate")
                                .doesNotContain("-SNAPSHOT is never published"));
            }
        }

        @Test
        void theOrderAndBreakingRulesHoldOverThePacksChangelog() {
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.0", "## 0.1.0\n\n## 0.2.0\n"))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("is not above the released 0.2.0 in contracts/packs/CHANGELOG.md"));
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.2.0", "## 0.2.0\n\n## 0.1.0\n"))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("of contracts/packs/CHANGELOG.md raises the minor version of 0.x")
                            .contains("### Breaking"));
            assertThat(ReleaseGate.packsReleaseRefusal(
                            "contract-packs-v0.2.0", "## 0.2.0\n\n### Breaking\n\n- x\n\n## 0.1.0\n"))
                    .isEmpty();
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.1", "## 0.1.1\n\n## 0.1.0\n"))
                    .isEmpty();
        }

        @Test
        void everyBuildChecksThePacksChangelogReleasedOrNot() {
            assertThat(ReleaseGate.packsChangelogRefusal("## 0.1.0-SNAPSHOT\n")).isEmpty();
            assertThat(ReleaseGate.packsChangelogRefusal(RELEASED)).isEmpty();
            assertThat(ReleaseGate.packsChangelogRefusal("## 0.2.0-SNAPSHOT\n\n## 0.1.0\n"))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("of contracts/packs/CHANGELOG.md raises the minor version of 0.x"));
            assertThat(ReleaseGate.packsChangelogRefusal("## 0.1.1\n\n## 0.1.0\n\n## 0.1.0\n"))
                    .hasValueSatisfying(r -> assertThat(r.message()).contains("lists 0.1.0 twice"));
            assertThat(ReleaseGate.packsChangelogRefusal("# Changelog\n\n## Unreleased\n"))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("The top heading 'Unreleased' of contracts/packs/CHANGELOG.md is not a version"));
            assertThat(ReleaseGate.packsChangelogRefusal("# Changelog\n"))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .isEqualTo("contracts/packs/CHANGELOG.md has no version heading (## X.Y.Z)."));
        }

        @Test
        void aChangelogWithoutAVersionHeadingIsRefusedByName() {
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.0", "# Changelog\n"))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .isEqualTo("contracts/packs/CHANGELOG.md has no version heading (## X.Y.Z)."));
        }
    }

    /**
     * Once {@code contract-packs-v<version>} is merged into HEAD, a change to a packs contract file under the released
     * heading is refused until the {@code -SNAPSHOT} is reopened (#663), as the charter contract refuses any commit
     * after its release. A released heading below the top without its merged tag fails closed.
     */
    @Nested
    class PacksReleasedHeading {

        private static final String RC2 = "contract-packs-v0.1.0-rc.2";
        private static final String RC3 = "contract-packs-v0.1.0-rc.3";
        private static final Set<String> BOTH = Set.of(RC2, RC3);
        private static final String CANDIDATE = "## 0.1.0-rc.3\n\n## 0.1.0-rc.2\n";

        @Test
        void aChangedPacksFileUnderAReleasedTaggedHeadingIsRefused() {
            assertThat(ReleaseGate.packsChangelogRefusal(CANDIDATE, BOTH, Set.of(RC3)))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("The top heading 0.1.0-rc.3 of contracts/packs/CHANGELOG.md is released")
                            .contains(RC3 + " is merged into HEAD")
                            .contains("differ from that release")
                            .endsWith("reopen ## 0.1.0-SNAPSHOT above ## 0.1.0-rc.3."));
        }

        @Test
        void aReleasedTaggedHeadingWithTheReleasedFilesPasses() {
            assertThat(ReleaseGate.packsChangelogRefusal(CANDIDATE, BOTH, NO_TAGS))
                    .isEmpty();
        }

        @Test
        void afterAFinalReleaseTheRefusalNamesTheNextSnapshotToOpen() {
            assertThat(ReleaseGate.packsChangelogRefusal(
                            "## 0.1.0\n\n## 0.1.0-rc.3\n",
                            Set.of("contract-packs-v0.1.0", RC3),
                            Set.of("contract-packs-v0.1.0")))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("contract-packs-v0.1.0 is merged into HEAD")
                            .endsWith("open the next -SNAPSHOT above ## 0.1.0."));
        }

        @Test
        void theReleasePullRequestPassesBeforeItsTagIsMerged() {
            // The pending release: the heading is released and the files differ from the previous release, but no tag
            // for the top heading is merged yet.
            assertThat(ReleaseGate.packsChangelogRefusal(CANDIDATE, Set.of(RC2), Set.of(RC2)))
                    .isEmpty();
        }

        @Test
        void aCloneWithoutTheTagsFailsClosed() {
            // A clone without tags (git clone --no-tags) lists none merged: the released rc.2 below the top names the
            // missing tag rather than letting a changed rc.3 pass unjudged.
            assertThat(ReleaseGate.packsChangelogRefusal(CANDIDATE, NO_TAGS, Set.of(RC3)))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("The released heading ## 0.1.0-rc.2 of contracts/packs/CHANGELOG.md has no " + RC2
                                    + " tag merged into HEAD")
                            .contains("git fetch --tags"));
        }

        @Test
        void aSnapshotHeadingIsNeverFrozen() {
            // A -SNAPSHOT is never published: neither a stray tag of its name nor the release below it freezes it.
            String snapshotTag = "contract-packs-v0.1.0-SNAPSHOT";
            assertThat(ReleaseGate.packsChangelogRefusal(
                            "## 0.1.0-SNAPSHOT\n\n" + CANDIDATE,
                            Set.of(RC2, RC3, snapshotTag),
                            Set.of(RC3, snapshotTag)))
                    .isEmpty();
        }

        @Test
        void theTopTagIsNamedByTheGatesOwnHeadingGrammar() {
            // A heading the gate reads as 0.1.0-rc.3 ("v" prefix, brackets, a date) names contract-packs-v0.1.0-rc.3.
            assertThat(ReleaseGate.packsChangelogRefusal(
                            "## [v0.1.0-rc.3] - 2026-12-01\n\n## 0.1.0-rc.2\n", BOTH, Set.of(RC3)))
                    .hasValueSatisfying(r -> assertThat(r.message()).contains(RC3 + " is merged into HEAD"));
        }
    }

    /** Operator ruling R1 (2026-11-16): the first contract releases are release candidates, X.Y.Z-<pre>. */
    @Nested
    class ReleaseCandidates {

        private static final String V010 = "contract-charter-v0.1.0";
        private static final String RC1 = "contract-charter-v0.2.0-rc.1";
        /** The first candidate of a new 0.x line, over the final 0.1.0: its section lists what breaks. */
        private static final String FIRST_RC = "## 0.2.0-rc.1\n\n### Breaking\n\n- x\n\n## 0.1.0\n";

        @Test
        void aCandidateTagThatNamesTheTopHeadingAndInfoVersionPasses() {
            assertThat(ReleaseGate.releaseRefusal(RC1, "0.2.0-rc.1", FIRST_RC)).isEmpty();
            assertThat(ReleaseGate.releaseRefusal("contract-charter-v0.1.0-rc.1", "0.1.0-rc.1", "## 0.1.0-rc.1\n"))
                    .isEmpty();
        }

        @Test
        void aCandidateTagForAnotherCandidateIsRefused() {
            assertThat(ReleaseGate.releaseRefusal("contract-charter-v0.2.0-rc.2", "0.2.0-rc.1", FIRST_RC))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("contract-charter-v0.2.0-rc.2 names 0.2.0-rc.2")
                            .contains("info.version is 0.2.0-rc.1"));
        }

        @Test
        void aMalformedPreReleaseIsRefusedSayingWhy() {
            String[][] cases = {
                {"0.2.0-rc-1", "not letters, digits and dots"},
                {"0.2.0-rc_1", "not letters, digits and dots"},
                {"0.2.0-rc+1", "not letters, digits and dots"},
                {"0.2.0+build.1", "build metadata"},
                {"0.2.0-rc.1+build.1", "not letters, digits and dots"},
                {"0.2.0-rc..1", "empty pre-release identifier"},
                {"0.2.0-rc.", "empty pre-release identifier"},
                {"0.2.0-", "not letters, digits and dots"},
                {"0.2.0-rc.01", "leading zero"},
                {"0.2.0-snapshot", "-SNAPSHOT, upper case and alone"},
                {"0.2.0-SNAPSHOT.1", "-SNAPSHOT, upper case and alone"},
            };
            for (String[] c : cases) {
                String heading = "## " + c[0] + "\n\n### Breaking\n\n- x\n\n## 0.1.0\n";
                assertThat(ReleaseGate.releaseRefusal("contract-charter-v" + c[0], c[0], heading))
                        .as(c[0])
                        .hasValueSatisfying(r -> assertThat(r.message())
                                .contains("is not a release (X.Y.Z) or a release candidate")
                                .contains(c[1]));
                assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v" + c[0], heading))
                        .as(c[0])
                        .hasValueSatisfying(r -> assertThat(r.message()).contains(c[1]));
            }
        }

        @Test
        void aMalformedPreReleaseHeadingIsRefusedWhereverItStands() {
            assertThat(ReleaseGate.decideWithoutGit(
                            "0.2.0-SNAPSHOT", changelog("## 0.2.0-SNAPSHOT\n", "## 0.2.0-rc-1\n", "## 0.1.0\n")))
                    .isInstanceOf(Decision.Refused.class)
                    .extracting(Decision::message)
                    .asString()
                    .contains("The heading '0.2.0-rc-1'")
                    .contains("not letters, digits and dots");
        }

        @Test
        void aPendingCandidateIsComparedWithThePreviousFinalReleaseOfItsLine() {
            assertThat(decide("0.1.1-rc.1", changelog("## 0.1.1-rc.1\n", "## 0.1.0\n"), Set.of(V010)))
                    .isEqualTo(new Decision.Compare(V010, "pending release 0.1.1-rc.1: compared against 0.1.0"));
        }

        @Test
        void theFirstCandidateOfANewLineHasNoBaseline() {
            Decision decision = decide("0.2.0-rc.1", FIRST_RC, Set.of(V010));
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message())
                    .contains("pending release 0.2.0-rc.1")
                    .contains("records no final 0.2.x release");
        }

        @Test
        void theTaggedCandidateIsComparedWithThePreviousFinalReleaseNotWithItself() {
            Decision decision = ReleaseGate.decide(
                    "0.1.1-rc.1",
                    changelog("## 0.1.1-rc.1\n", "## 0.1.0\n"),
                    Set.of(V010, "contract-charter-v0.1.1-rc.1"),
                    Set.of("contract-charter-v0.1.1-rc.1"));
            assertThat(decision).isEqualTo(new Decision.Compare(V010, "compared against 0.1.0"));
        }

        @Test
        void aCommitAfterTheCandidateTagUnderTheCandidateHeadingIsRefused() {
            Decision decision = decide("0.2.0-rc.1", FIRST_RC, Set.of(V010, RC1));
            assertThat(decision).isInstanceOf(Decision.Refused.class);
            assertThat(decision.message())
                    .contains("HEAD is not that release")
                    .contains("after a release candidate it is 0.2.0-SNAPSHOT");
        }

        @Test
        void afterACandidateTheSameSnapshotIsComparedWithThePreviousFinalRelease() {
            String log = changelog("## 0.1.1-SNAPSHOT\n", "## 0.1.1-rc.1\n", "## 0.1.0\n");
            assertThat(decide("0.1.1-SNAPSHOT", log, Set.of(V010, "contract-charter-v0.1.1-rc.1")))
                    .isEqualTo(new Decision.Compare(V010, "compared against 0.1.0"));
        }

        @Test
        void afterTheFirstCandidateOfANewLineTheSnapshotHasNoBaselineAndNeedsNoSecondBreaking() {
            String log = changelog("## 0.2.0-SNAPSHOT\n\n### Added\n\n- y\n", FIRST_RC);
            Decision decision = decide("0.2.0-SNAPSHOT", log, Set.of(V010, RC1));
            assertThat(decision).isInstanceOf(Decision.NoBaseline.class);
            assertThat(decision.message()).contains("records no final 0.2.x release");
            assertThat(ReleaseGate.previousBreakingBaseline("0.2.0-SNAPSHOT", log, Set.of(V010, RC1)))
                    .contains(V010);
        }

        @Test
        void aCandidateNeverCountsAsThePreviousReleaseForTheBreakingRule() {
            // 0.2.0-rc.1 holds no Breaking, so the next candidate of the line is still a bump over 0.1.0.
            String log = changelog("## 0.2.0-rc.2\n", "## 0.2.0-rc.1\n", "## 0.1.0\n");
            assertThat(ReleaseGate.decideWithoutGit("0.2.0-rc.2", log).message())
                    .contains("raises the minor version of 0.x from 0.1.0 to 0.2.0-rc.2");
            assertThat(ReleaseGate.decideWithoutGit("0.2.0-rc.1", FIRST_RC.replace("### Breaking\n\n", "")))
                    .isInstanceOf(Decision.Refused.class);
        }

        @Test
        void theNextCandidateMayFollowACandidate() {
            String log = changelog("## 0.2.0-rc.2\n", FIRST_RC);
            assertThat(ReleaseGate.releaseRefusal("contract-charter-v0.2.0-rc.2", "0.2.0-rc.2", log))
                    .isEmpty();
            assertThat(decide("0.2.0-rc.2", log, Set.of(V010, RC1))).isInstanceOf(Decision.NoBaseline.class);
        }

        @Test
        void theFinalReleaseMayFollowItsCandidatesAndIsTheNextBaseline() {
            String log = changelog("## 0.2.0\n", "## 0.2.0-rc.2\n", FIRST_RC);
            assertThat(ReleaseGate.releaseRefusal("contract-charter-v0.2.0", "0.2.0", log))
                    .isEmpty();
            Set<String> tags = Set.of(V010, RC1, "contract-charter-v0.2.0-rc.2", "contract-charter-v0.2.0");
            assertThat(decide("0.2.1-SNAPSHOT", changelog("## 0.2.1-SNAPSHOT\n", log), tags))
                    .isEqualTo(new Decision.Compare("contract-charter-v0.2.0", "compared against 0.2.0"));
        }

        @Test
        void anotherVersionAfterACandidateIsRefusedUntilItsFinalIsRecorded() {
            for (String next : new String[] {"0.2.1-SNAPSHOT", "0.3.0-SNAPSHOT", "0.2.1-rc.1", "1.0.0"}) {
                String log = changelog("## " + next + "\n\n### Breaking\n\n- z\n", FIRST_RC);
                assertThat(ReleaseGate.decideWithoutGit(next, log).message())
                        .as(next)
                        .contains("follows the release candidate 0.2.0-rc.1")
                        .contains("0.2.0-SNAPSHOT, a later candidate of 0.2.0 or the final 0.2.0");
            }
        }

        @Test
        void aCandidateRanksBelowItsSnapshotAndItsFinalRelease() {
            assertThat(ReleaseGate.decideWithoutGit("0.2.0-rc.1", changelog("## 0.2.0-rc.1\n", "## 0.2.0\n"))
                            .message())
                    .contains("is not above the released 0.2.0");
            assertThat(ReleaseGate.decideWithoutGit("0.2.0-rc.1", changelog("## 0.2.0-rc.1\n", "## 0.2.0-rc.2\n"))
                            .message())
                    .contains("is not above the released 0.2.0-rc.2");
            assertThat(ReleaseGate.decideWithoutGit("0.2.0-rc.1", changelog("## 0.2.0-rc.1\n", "## 0.2.0-SNAPSHOT\n"))
                            .message())
                    .contains("not in descending order");
        }

        @Test
        void candidatesAreOrderedBySemVerPrecedence() {
            String log = changelog(
                    "## 1.0.0-rc.10\n",
                    "## 1.0.0-rc.9\n",
                    "## 1.0.0-rc.1.1\n",
                    "## 1.0.0-rc.1\n",
                    "## 1.0.0-beta\n",
                    "## 1.0.0-alpha.beta\n",
                    "## 1.0.0-alpha.1\n",
                    "## 1.0.0-alpha\n");
            assertThat(ReleaseGate.decideWithoutGit("1.0.0-rc.10", log)).isInstanceOf(Decision.Skipped.class);
            assertThat(ReleaseGate.decideWithoutGit(
                                    "1.0.0-alpha.1", changelog("## 1.0.0-alpha.1\n", "## 1.0.0-alpha.beta\n"))
                            .message())
                    .contains("is not above the released 1.0.0-alpha.beta");
        }

        @Test
        void aCandidateTagIsNeverABaseline() {
            assertThat(ReleaseGate.baselineRefusal(RC1, "0.2.0-rc.1")).isPresent();
        }

        @Test
        void aMergedCandidateTagTheChangelogDoesNotRecordIsRefused() {
            assertThat(decide("0.1.1-SNAPSHOT", changelog("## 0.1.1-SNAPSHOT\n", "## 0.1.0\n"), Set.of(V010, RC1))
                            .message())
                    .contains("[" + RC1 + "] are merged into HEAD");
        }

        @Test
        void thePacksContractTakesTheSameCandidates() {
            String log = "## 0.1.0-rc.1\n";
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.0-rc.1", log))
                    .isEmpty();
            assertThat(ReleaseGate.packsChangelogRefusal("## 0.1.0-SNAPSHOT\n\n" + log))
                    .isEmpty();
            assertThat(ReleaseGate.packsChangelogRefusal("## 0.1.1-SNAPSHOT\n\n" + log))
                    .hasValueSatisfying(r -> assertThat(r.message())
                            .contains("follows the release candidate 0.1.0-rc.1, whose final release is not recorded")
                            .contains("contracts/packs/CHANGELOG.md"));
            assertThat(ReleaseGate.packsReleaseRefusal("contract-packs-v0.1.0-rc.2", log))
                    .hasValueSatisfying(r -> assertThat(r.message()).contains("at that commit is 0.1.0-rc.1"));
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
