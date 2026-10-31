package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PackPolicyTest {

    /** A fragment that was read and is empty: an org pack has one, so no {@code org_fragment_missing} fires. */
    private static final FileRead<RawGraph> EMPTY_FRAGMENT = new FileRead.Read<>(
            "drg/fragment.yaml", new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));

    private static FileRead<PackDescriptor> descriptor(String name, Optional<PackLevel> declared) {
        return new FileRead.Read<>(
                "pack.yaml", new PackDescriptor("01ABC", "1.0.0", Optional.empty(), name, declared, Optional.empty()));
    }

    private static PackContents contents(
            String name, Optional<PackLevel> configured, FileRead<PackDescriptor> descriptor) {
        return new PackContents(
                new PackRoot(name, configured, "loc"),
                descriptor,
                List.of(new FileRead.Read<>(
                        "a.graph.yaml", new RawGraph("a.graph.yaml", RawGraph.Shape.SHARD, List.of(), List.of()))),
                EMPTY_FRAGMENT,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void anAbsentDescriptorIsAMissingWarningNamingTheConfiguredLevel() {
        assertThat(PackPolicy.level("qa", Optional.of(PackLevel.ORG), new FileRead.Absent<>("pack.yaml")))
                .containsExactly(new PackFinding(
                        PackFindingCode.PACK_LEVEL_MISSING,
                        "pack.yaml",
                        List.of(),
                        "org pack qa has no pack.yaml; the configured level org is used"));
    }

    @Test
    void aDescriptorWithoutALevelIsAMissingWarning() {
        assertThat(PackPolicy.level("internal", Optional.of(PackLevel.ORG), descriptor("internal", Optional.empty())))
                .containsExactly(new PackFinding(
                        PackFindingCode.PACK_LEVEL_MISSING,
                        "pack.yaml",
                        List.of(),
                        "pack.yaml of internal has no pack_level; the configured level org is used"));
    }

    @Test
    void aDeclaredLevelThatDiffersIsABlockingMismatch() {
        List<PackFinding> findings = PackPolicy.level(
                "rogue", Optional.of(PackLevel.ORG), descriptor("rogue", Optional.of(PackLevel.BUILT_IN)));
        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.PACK_LEVEL_MISMATCH,
                        "pack.yaml",
                        List.of(),
                        "Pack 'rogue' is configured as org but declares pack_level built-in"));
        assertThat(findings.get(0).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    void aDeclaredLevelThatEqualsTheConfiguredOneIsNoFinding() {
        assertThat(PackPolicy.level("qa", Optional.of(PackLevel.ORG), descriptor("qa", Optional.of(PackLevel.ORG))))
                .isEmpty();
    }

    @Test
    void aBarePathHasNoLevelFinding() {
        assertThat(PackPolicy.level("qa", Optional.empty(), new FileRead.Absent<>("pack.yaml")))
                .isEmpty();
        assertThat(PackPolicy.level("qa", Optional.empty(), descriptor("qa", Optional.empty())))
                .isEmpty();
        assertThat(PackPolicy.level("qa", Optional.empty(), descriptor("qa", Optional.of(PackLevel.BUILT_IN))))
                .isEmpty();
    }

    @Test
    void anUnreadableDescriptorHasNoLevelFinding() {
        assertThat(PackPolicy.level(
                        "qa",
                        Optional.of(PackLevel.ORG),
                        new FileRead.Unreadable<>(
                                "pack.yaml", new PackContents.ParseFailure("pack.yaml", "x"), List.of())))
                .isEmpty();
    }

    @Test
    void judgeAggregatesTheLevelFindings() {
        PackVerdict verdict =
                PackPolicy.judge(contents("qa", Optional.of(PackLevel.ORG), new FileRead.Absent<>("pack.yaml")));
        assertThat(verdict.findings())
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.PACK_LEVEL_MISSING);
    }

    @Test
    void loadRefusesWithTheMismatchAndItsDetail() {
        PackContents c =
                contents("rogue", Optional.of(PackLevel.ORG), descriptor("rogue", Optional.of(PackLevel.BUILT_IN)));
        assertThatThrownBy(() -> Judged.refuseOnFirstBlocking(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_LEVEL_MISMATCH);
            assertThat(r.getMessage()).isEqualTo("Pack 'rogue' is configured as org but declares pack_level built-in");
        });
    }

    @Test
    void aMissingPackYamlIsAWarningWithItsPinnedText() {
        PackContents c = contents("qa", Optional.of(PackLevel.ORG), new FileRead.Absent<>("pack.yaml"));
        assertThat(Judged.diagnostics(c))
                .containsExactly(new Diagnostic(
                        Diagnostic.Code.PACK_LEVEL_MISSING,
                        "org pack qa has no pack.yaml; the configured level org is used"));
    }

    // ---- the first blocking finding refuses, a graph finding among them -----------------------------------------

    private static PackContents withShard(
            FileRead<RawGraph> shard, FileRead<RawGraph> fragment, FileRead<PackDescriptor> descriptor) {
        return new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                descriptor,
                List.of(shard),
                fragment,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static RawGraph shard(String file, List<PackContents.RawNode> nodes, List<PackContents.RawEdge> edges) {
        return new RawGraph(file, RawGraph.Shape.SHARD, nodes, edges);
    }

    private static PackContents.RawEdge edge(String source, String target, String relation) {
        return new PackContents.RawEdge(source, target, relation, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void loadKeepsTheWarningsAsDiagnostics() {
        PackContents c = withShard(
                new FileRead.Read<>("a.graph.yaml", shard("a.graph.yaml", List.of(), List.of())),
                EMPTY_FRAGMENT,
                new FileRead.Absent<>("pack.yaml"));
        assertThat(Judged.diagnostics(c))
                .extracting(Diagnostic::code)
                .containsExactly(Diagnostic.Code.PACK_LEVEL_MISSING);
    }

    @Test
    void loadRefusesWithTheFirstGraphFindingBeforeBuildingAnything() {
        PackContents c = withShard(
                new FileRead.Read<>(
                        "a.graph.yaml",
                        shard("a.graph.yaml", List.of(), List.of(edge("tactic:a", "QA_404", "requires")))),
                EMPTY_FRAGMENT,
                descriptor("qa", Optional.of(PackLevel.ORG)));
        assertThatThrownBy(() -> Judged.refuseOnFirstBlocking(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
            assertThat(r.getMessage()).isEqualTo("qa/a.graph.yaml: edge endpoint 'QA_404' is not a URN");
        });
    }

    @Test
    void aHandEditedTensionInTheRegisteredShardLoadsCleanWhileARegistrationRewriteReportsIt() {
        // ADR-0044 decision 5: load and validation use the ordinary graph rules on registered.graph.yaml, so the pack
        // stays loadable; only a registration rewrite, which would drop the tension, reports it. Deliberate until the
        // operator chooses between refusing it in validation and carrying tensions through a rewrite.
        RawGraph registered = shard(
                "registered.graph.yaml", List.of(), List.of(edge("directive:a", "directive:b", "in_tension_with")));
        PackContents c = withShard(
                new FileRead.Read<>("registered.graph.yaml", registered),
                EMPTY_FRAGMENT,
                descriptor("qa", Optional.of(PackLevel.ORG)));

        assertThat(PackPolicy.judge(c).findings()).isEmpty();
        assertThat(PackPolicy.registeredGraph("qa", registered).findings())
                .extracting(PackFinding::detail)
                .containsExactly(
                        "qa/registered.graph.yaml: a registered relationship is never a tension; in_tension_with"
                                + " is authored in a pack's graph shards");
    }

    @Test
    void loadRefusesAnUnreadableDescriptorWithItsOwnTextAndCode() {
        PackContents c = withShard(
                new FileRead.Read<>("a.graph.yaml", shard("a.graph.yaml", List.of(), List.of())),
                EMPTY_FRAGMENT,
                new FileRead.Unreadable<>(
                        "pack.yaml", new PackContents.ParseFailure("pack.yaml", "while scanning"), List.of()));
        assertThatThrownBy(() -> Judged.refuseOnFirstBlocking(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(r.getMessage()).isEqualTo("pack.yaml of qa is not valid YAML");
        });
        assertThatThrownBy(() -> Judged.refuseOnFirstBlocking(c))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void loadRefusesAnUnreadableShardWithDrgSchemaErrorAndTheParseText() {
        PackContents c = withShard(
                new FileRead.Unreadable<>(
                        "a.graph.yaml",
                        new PackContents.ParseFailure("a.graph.yaml", "while parsing a block mapping"),
                        List.of()),
                EMPTY_FRAGMENT,
                descriptor("qa", Optional.of(PackLevel.ORG)));
        assertThatThrownBy(() -> Judged.refuseOnFirstBlocking(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
            assertThat(r.getMessage()).isEqualTo("qa/a.graph.yaml is not valid YAML: while parsing a block mapping");
        });
    }

    @Test
    void anUnreadableFileHasExactlyOneOfAParseFailureAndSchemaViolations() {
        PackContents.ParseFailure parse = new PackContents.ParseFailure("a.yaml", "while scanning");
        PackContents.SchemaViolation violation = new PackContents.SchemaViolation(
                "a.yaml", List.of("x"), "type", Optional.empty(), Optional.empty(), java.util.Map.of());

        assertThatThrownBy(() -> new FileRead.Unreadable<RawGraph>("a.yaml", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.yaml");
        assertThatThrownBy(() -> new FileRead.Unreadable<RawGraph>("a.yaml", parse, List.of(violation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a.yaml");
        assertThat(new FileRead.Unreadable<RawGraph>("a.yaml", parse, List.of()).violations())
                .isEmpty();
        assertThat(new FileRead.Unreadable<RawGraph>("a.yaml", null, List.of(violation)).parse())
                .isNull();
    }

    // ---- the keywords the vocabulary has a row for ----------------------------------------------------

    private static PackContents.SchemaViolation violation(
            String file,
            List<String> pointer,
            String keyword,
            String property,
            String value,
            Map<String, String> args) {
        return new PackContents.SchemaViolation(
                file, pointer, keyword, Optional.ofNullable(property), Optional.ofNullable(value), args);
    }

    private static String textOf(PackContents.SchemaViolation violation) {
        return PackPolicy.schemaFindings("qa", List.of(violation)).get(0).detail();
    }

    /** One canonical violation, with the facts the vocabulary needs, for each keyword that has a row. */
    static Stream<Arguments> keywordsWithARow() {
        String fragment = "drg/fragment.yaml";
        return Stream.of(
                Arguments.of(
                        "type",
                        violation(
                                "pack.yaml",
                                List.of("pack_id"),
                                "type",
                                null,
                                "x",
                                Map.of("0", "array", "1", "string"))),
                Arguments.of(
                        "additionalProperties",
                        violation("a.graph.yaml", List.of(), "additionalProperties", "x", null, Map.of())),
                Arguments.of("required", violation("a.graph.yaml", List.of(), "required", "nodes", null, Map.of())),
                Arguments.of(
                        "minLength",
                        violation(
                                fragment,
                                List.of("nodes", "0", "id"),
                                "minLength",
                                null,
                                "",
                                Map.of("kind", "directives"))),
                Arguments.of(
                        "pattern",
                        violation(
                                fragment,
                                List.of("nodes", "0", "id"),
                                "pattern",
                                null,
                                "a b",
                                Map.of("kind", "directives"))),
                Arguments.of("enum", violation("pack.yaml", List.of("pack_level"), "enum", null, "x", Map.of())),
                Arguments.of(
                        "const", violation("a.graph.yaml", List.of("schema_version"), "const", null, "2", Map.of())),
                Arguments.of(
                        "minItems",
                        violation(
                                "a.graph.yaml", List.of("nodes"), "minItems", null, null, Map.of("0", "1", "1", "0"))),
                Arguments.of("minimum", violation("a.graph.yaml", List.of("n"), "minimum", null, "-5", Map.of())),
                Arguments.of("maximum", violation("a.graph.yaml", List.of("n"), "maximum", null, "5", Map.of())),
                Arguments.of(
                        "exclusiveMinimum",
                        violation("a.graph.yaml", List.of("n"), "exclusiveMinimum", null, "0", Map.of())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keywordsWithARow")
    void aKeywordWithARowIsHandledAndGetsATextOfItsOwn(String keyword, PackContents.SchemaViolation violation) {
        assertThat(PackPolicy.handles(keyword)).isTrue();
        assertThat(textOf(violation)).doesNotContain("breaks schema keyword");
    }

    @ParameterizedTest
    @ValueSource(strings = {"anyOf", "oneOf", "format", "dependentRequired", "contains", ""})
    void aKeywordWithoutARowIsNotHandledAndGetsTheFixedFallback(String keyword) {
        assertThat(PackPolicy.handles(keyword)).isFalse();
        assertThat(textOf(violation("a.graph.yaml", List.of("nodes", "0"), keyword, null, null, Map.of())))
                .isEqualTo("qa/a.graph.yaml: '/nodes/0' breaks schema keyword '" + keyword + "'");
    }

    @Test
    void aRetiredFileReadsAsRetiredAndAParseFailureKeepsItsWording() {
        PackContents.ParseFailure retired =
                PackContents.ParseFailure.retired("missions/plan/mission.yaml", "folded into the mission-type file");
        PackContents.ParseFailure markdown =
                PackContents.ParseFailure.retired("mission-steps/plan/specify/prompt.md", "folded into the steps");
        PackContents.ParseFailure broken = new PackContents.ParseFailure("tactics/x.tactic.yaml", "mapping expected");

        assertThat(retired.refusal()).contains("is retired: folded into the mission-type file");
        assertThat(broken.refusal()).isEmpty();
        assertThat(PackPolicy.unreadable("qa", new FileRead.Unreadable<>(retired.file(), retired, List.of())))
                .extracting(PackFinding::detail)
                .containsExactly("qa/missions/plan/mission.yaml is retired: folded into the mission-type file");
        assertThat(PackPolicy.unreadable("qa", new FileRead.Unreadable<>(markdown.file(), markdown, List.of())))
                .extracting(PackFinding::detail)
                .containsExactly("qa/mission-steps/plan/specify/prompt.md is retired: folded into the steps");
        assertThat(PackPolicy.unreadable("qa", new FileRead.Unreadable<>(broken.file(), broken, List.of())))
                .extracting(PackFinding::detail)
                .containsExactly("qa/tactics/x.tactic.yaml is not valid YAML: mapping expected");
    }
}
