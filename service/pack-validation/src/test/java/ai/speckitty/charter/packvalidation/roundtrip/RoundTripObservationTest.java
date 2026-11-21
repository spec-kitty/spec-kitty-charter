package ai.speckitty.charter.packvalidation.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The verdict over a round trip's result, by example: {@link RoundTripObservation} compares each side the flow read, in
 * its form, and {@link WholePackRoundTripRule} holds the observation to an expectation. Each result is built by hand, so
 * every branch is reached without a pack on disk.
 */
class RoundTripObservationTest {

    private static final String TACTIC = "tactics/a.tactic.yaml";

    private static Reading.Yaml yaml(Object tree) {
        return new Reading.Yaml(tree);
    }

    private static PackRoundTripResult result(
            Map<String, String> census, Map<String, FileOutcome> outcomes, Set<String> written) {
        return result(census, Map.of(), outcomes, written);
    }

    /** A result whose modelled kinds are the kinds of the files the census holds. */
    private static PackRoundTripResult result(
            Map<String, String> census,
            Map<String, NotHeldFile> notHeld,
            Map<String, FileOutcome> outcomes,
            Set<String> written) {
        Set<String> kinds = new TreeSet<>();
        census.forEach((path, kind) -> {
            if (!notHeld.containsKey(path)) {
                kinds.add(kind);
            }
        });
        return new PackRoundTripResult(
                new TreeMap<>(census), new TreeMap<>(notHeld), kinds, new TreeMap<>(outcomes), new TreeSet<>(written));
    }

    @Nested
    class TheObservation {

        @Test
        void aYamlFileIsComparedByMeaningAndPairsItsWrittenFile() {
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of(TACTIC, "tactic", "tactics/b.tactic.yaml", "tactic"),
                    Map.of(
                            TACTIC,
                            new FileOutcome.Written("a.tactic.yaml", yaml(Map.of("id", "a")), yaml(Map.of("id", "a"))),
                            "tactics/b.tactic.yaml",
                            new FileOutcome.Written(
                                    "b.tactic.yaml", yaml(Map.of("id", "b", "purpose", "p")), yaml(Map.of("id", "b")))),
                    Set.of("a.tactic.yaml", "b.tactic.yaml", "stray.yaml")));

            assertThat(observation.observed())
                    .containsEntry(TACTIC, Optional.empty())
                    .containsEntry("tactics/b.tactic.yaml", Optional.of("purpose: source p, written <absent>"));
            assertThat(observation.unpaired()).containsExactly("stray.yaml");
            assertThat(observation.information()).isEmpty();
        }

        @Test
        void aRefusedFileIsRedWithItsReason() {
            RoundTripObservation observation = RoundTripObservation.of(
                    result(Map.of(TACTIC, "tactic"), Map.of(TACTIC, new FileOutcome.Refused("id: missing")), Set.of()));

            assertThat(observation.observed()).containsExactly(Map.entry(TACTIC, Optional.of("refused: id: missing")));
        }

        @Test
        void bytesAreComparedByOffsetThenByLength() {
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of(
                            "assets/same.png",
                            "asset-payload",
                            "assets/changed.png",
                            "asset-payload",
                            "t.png",
                            "template"),
                    Map.of(
                            "assets/same.png",
                            new FileOutcome.Written(
                                    "assets/same.png",
                                    new Reading.Bytes(new byte[] {1, 2}),
                                    new Reading.Bytes(new byte[] {1, 2})),
                            "assets/changed.png",
                            new FileOutcome.Written(
                                    "assets/changed.png",
                                    new Reading.Bytes(new byte[] {1, 2, 3}),
                                    new Reading.Bytes(new byte[] {1, (byte) 0xff, 3})),
                            "t.png",
                            new FileOutcome.Written(
                                    "t.png", new Reading.Bytes(new byte[] {1, 2}), new Reading.Bytes(new byte[] {1}))),
                    Set.of("assets/same.png", "assets/changed.png", "t.png")));

            assertThat(observation.observed())
                    .containsEntry("assets/same.png", Optional.empty())
                    .containsEntry("assets/changed.png", Optional.of("bytes[1]: source 0x02, written 0xff"))
                    .containsEntry("t.png", Optional.of("length: source 2, written 1"));
            assertThat(observation.unpaired()).isEmpty();
        }

        @Test
        void textIsComparedExactlyByOffsetThenByLength() {
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of("a.txt", "skill", "b.txt", "skill", "c.txt", "skill"),
                    Map.of(
                            "a.txt", new FileOutcome.Written("a.txt", new Reading.Text("abc"), new Reading.Text("abc")),
                            "b.txt", new FileOutcome.Written("b.txt", new Reading.Text("abc"), new Reading.Text("abd")),
                            "c.txt", new FileOutcome.Written("c.txt", new Reading.Text("abc"), new Reading.Text("ab"))),
                    Set.of("a.txt", "b.txt", "c.txt")));

            assertThat(observation.observed())
                    .containsEntry("a.txt", Optional.empty())
                    .containsEntry("b.txt", Optional.of("text[2]: source 'c', written 'd'"))
                    .containsEntry("c.txt", Optional.of("length: source 3, written 2"));
        }

        @Test
        void markdownIsComparedByModelAndARefusalNamesItself() {
            MarkdownDocument document = MarkdownDocument.empty();
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of("README.md", "pack-document", "b.md", "pack-document"),
                    Map.of(
                            "README.md",
                            new FileOutcome.Written(
                                    "README.md", new Reading.Markdown(document), new Reading.Markdown(document)),
                            "b.md",
                            new FileOutcome.Written(
                                    "b.md", new Reading.Markdown(document), new Reading.MarkdownRefused("line 2: no"))),
                    Set.of("README.md", "b.md")));

            assertThat(observation.observed())
                    .containsEntry("README.md", Optional.empty())
                    .containsEntry("b.md", Optional.of("document: source read, written refused: line 2: no"));
        }

        /** #630: only the written side's stale fields are the round trip's; the source side's are manifest_stale. */
        @Test
        void theWrittenManifestsStaleFieldsJoinItsDetail() {
            Map<String, Object> manifest = Map.of("schema_version", 1);
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of("pack-manifest.yaml", "pack-manifest", "other.yaml", "pack-manifest"),
                    Map.of(
                            "pack-manifest.yaml",
                            new FileOutcome.Written(
                                    "pack-manifest.yaml",
                                    yaml(manifest),
                                    yaml(Map.of("schema_version", 2)),
                                    List.of("b.content_hash", "c.content_hash")),
                            "other.yaml",
                            new FileOutcome.Written("other.yaml", yaml(manifest), yaml(manifest))),
                    Set.of("pack-manifest.yaml", "other.yaml")));

            assertThat(observation.observed())
                    .containsEntry(
                            "pack-manifest.yaml",
                            Optional.of("schema_version: source 1, written 2 ; written stale: b.content_hash,"
                                    + " c.content_hash"))
                    .containsEntry("other.yaml", Optional.empty());
        }

        @Test
        void anExcludedFileIsRedOnlyWhenTheStoreWroteIt() {
            String excluded = PackRoundTripResult.EXCLUDED + "**/*.md";
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of("docs/kept.md", excluded, "docs/written.md", excluded),
                    Map.of(
                            "docs/kept.md", NotHeldFile.excluded("docs/kept.md", "notes"),
                            "docs/written.md", NotHeldFile.excluded("docs/written.md", "notes")),
                    Map.of(),
                    Set.of("docs/written.md")));

            assertThat(observation.observed())
                    .containsEntry("docs/kept.md", Optional.empty())
                    .containsEntry("docs/written.md", Optional.of("the store wrote the not-held file docs/written.md"));
        }

        @Test
        void aFileNoRuleCoversIsNotHeldSoItIsRedOnlyWhenTheStoreWroteIt() {
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of("qa/kept.txt", PackRoundTripResult.OTHER + "qa", "notes.txt", "other:notes.txt"),
                    Map.of(
                            "qa/kept.txt", NotHeldFile.unmodelled("qa/kept.txt"),
                            "notes.txt", NotHeldFile.unmodelled("notes.txt")),
                    Map.of(),
                    Set.of("notes.txt")));

            assertThat(observation.observed())
                    .containsEntry("qa/kept.txt", Optional.empty())
                    .containsEntry("notes.txt", Optional.of("the store wrote the not-held file notes.txt"));
        }

        @Test
        void heldnessIsTheCensussNotTheKindsSoAStrayFileNamedSymlinkIsNotHeldAndAPolicyNothingNamesToo() {
            // #631 squad: a root file named symlink takes the link's kind, and an unnamed policy.yaml a modelled kind;
            // the census does not hold either, so each is checked as not held, never compared.
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of(
                            "symlink", PackRoundTripResult.SYMLINK,
                            "policy.yaml", "policy",
                            "procedures/p.procedure.yaml", "procedure"),
                    Map.of(
                            "symlink", NotHeldFile.unmodelled("symlink"),
                            "policy.yaml", NotHeldFile.unnamedPolicy("policy.yaml")),
                    Map.of(),
                    Set.of()));

            assertThat(observation.observed())
                    .as("a held file of an unmodelled schema gets no not-held check: the verdict fails it (#547)")
                    .containsOnly(entry("symlink", Optional.empty()), entry("policy.yaml", Optional.empty()));
        }

        @Test
        void aShardsDuplicateEdgesAreInformationNotADifference() {
            Map<String, Object> edge = Map.of("source", "a", "target", "b", "relation", "requires");
            Map<String, Object> shard = Map.of("nodes", List.of(), "edges", List.of(edge, edge));
            RoundTripObservation observation = RoundTripObservation.of(result(
                    Map.of("t.graph.yaml", "graph-shard", "u.graph.yaml", "graph-shard"),
                    Map.of(
                            "t.graph.yaml",
                            new FileOutcome.Written(
                                    "t.graph.yaml",
                                    yaml(shard),
                                    yaml(Map.of("nodes", List.of(), "edges", List.of(edge)))),
                            "u.graph.yaml",
                            new FileOutcome.Written(
                                    "u.graph.yaml",
                                    yaml(Map.of("nodes", List.of(), "edges", List.of(edge))),
                                    yaml(Map.of("nodes", List.of(), "edges", List.of(edge))))),
                    Set.of("t.graph.yaml", "u.graph.yaml")));

            assertThat(observation.observed()).containsEntry("t.graph.yaml", Optional.empty());
            assertThat(observation.information())
                    .containsOnlyKeys("t.graph.yaml")
                    .hasEntrySatisfying(
                            "t.graph.yaml",
                            lines -> assertThat(lines)
                                    .singleElement()
                                    .asString()
                                    .startsWith("duplicate edge in the source: "));
        }
    }

    @Nested
    class TheRule {

        private final PackRoundTripResult green = result(
                Map.of("pack.yaml", "pack-descriptor"),
                Map.of(
                        "pack.yaml",
                        new FileOutcome.Written("pack.yaml", yaml(Map.of("id", "p")), yaml(Map.of("id", "p")))),
                Set.of("pack.yaml"));

        @Test
        void aGreenRunHasNoReason() {
            WholePackRoundTripRule rule = new WholePackRoundTripRule(WholePackExpectation.none());

            assertThat(rule.apply(green)).isEmpty();
            assertThat(rule.check(green).isPassing()).isTrue();
        }

        @Test
        void anUnmodelledKindAndAnUnpairedFileAreViolationsWithTheirCodes() {
            PackRoundTripResult result = new PackRoundTripResult(
                    new TreeMap<>(Map.of("pack.yaml", "pack-descriptor", "drg/fragment.yaml", "org-fragment")),
                    new TreeMap<>(),
                    Set.of("pack-descriptor"),
                    new TreeMap<>(green.outcomes()),
                    new TreeSet<>(Set.of("pack.yaml", "extra.yaml")));

            List<Reason> reasons = new WholePackRoundTripRule(WholePackExpectation.none()).apply(result);

            assertThat(reasons)
                    .extracting(Reason::code, Reason::location, Reason::detail)
                    .containsExactly(
                            tuple(
                                    WholePackRoundTripRule.RED,
                                    Location.in("drg/fragment.yaml"),
                                    "UNEXPECTED_RED drg/fragment.yaml (org-fragment): no adapter models kind org-fragment"),
                            tuple(
                                    WholePackRoundTripRule.UNPAIRED,
                                    Location.in("extra.yaml"),
                                    "written but unpaired extra.yaml"));
            assertThat(reasons)
                    .allSatisfy(reason -> assertThat(reason.severity()).isEqualTo(Severity.VIOLATION));
        }

        @Test
        void aProblemIsPlacedAtTheCensusFileItNamesAndAProblemOfTheRunIsNot() {
            Set<String> census = Set.of("pack.yaml", "tactics/a.tactic.yaml");

            assertThat(WholePackRoundTripRule.locationOf("UNEXPECTED_RED tactics/a.tactic.yaml (tactic): x", census))
                    .isEqualTo(Location.in("tactics/a.tactic.yaml"));
            assertThat(WholePackRoundTripRule.locationOf(
                            "STALE_BASELINE built-in/x.yaml: names no example pack", census))
                    .isEqualTo(Location.NOWHERE);
            assertThat(WholePackRoundTripRule.locationOf("UNEXPECTED_RED tactics/b.tactic.yaml (tactic): x", census))
                    .isEqualTo(Location.NOWHERE);
            assertThat(WholePackRoundTripRule.locationOf("ALONE", census)).isEqualTo(Location.NOWHERE);
        }

        @Test
        void theWrittenCopyOfAFileListedAsRefusedMayStayUnpaired() {
            String red = """
                    files:
                    - path: built-in/tactics/a.tactic.yaml
                      at: 'refused: id: missing'
                      reason: r
                      issue: '#1'
                    """;
            WholePackExpectation expectation =
                    WholePackExpectation.load(red, "exclusions: []\n").forPack("built-in");
            PackRoundTripResult result = result(
                    Map.of(TACTIC, "tactic"),
                    Map.of(TACTIC, new FileOutcome.Refused("id: missing")),
                    Set.of(TACTIC, "other.yaml"));

            WholePackRoundTripRule.Verdict verdict = new WholePackRoundTripRule(expectation).judge(result);

            assertThat(verdict.evaluation().problems()).isEmpty();
            assertThat(verdict.unpaired())
                    .as("only the refused file's own path is excused")
                    .containsExactly("other.yaml");
        }
    }
}
