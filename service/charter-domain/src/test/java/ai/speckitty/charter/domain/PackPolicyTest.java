package ai.speckitty.charter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.RawGraph;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PackPolicyTest {

    private static FileRead<PackDescriptor> descriptor(String name, Optional<PackLevel> declared) {
        return new FileRead.Read<>(
                "pack.yaml",
                new PackDescriptor("01ABC", "1.0.0", Optional.empty(), name, declared, Optional.empty(), List.of()));
    }

    private static PackContents contents(
            String name, Optional<PackLevel> configured, FileRead<PackDescriptor> descriptor) {
        return new PackContents(
                new PackRoot(name, configured, "loc"),
                descriptor,
                List.of(new FileRead.Read<>(
                        "a.graph.yaml", new RawGraph("a.graph.yaml", RawGraph.Shape.SHARD, List.of(), List.of()))),
                new FileRead.Absent<>("drg/fragment.yaml"),
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
                        "qa", Optional.of(PackLevel.ORG), new FileRead.Unreadable<>("pack.yaml", null, List.of())))
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
    void theLoadPathIsThePackYamlTheRootShardsAndTheFragment() {
        assertThat(PackPolicy.loadPath(contents("qa", Optional.of(PackLevel.ORG), new FileRead.Absent<>("pack.yaml"))))
                .containsExactly("pack.yaml", "a.graph.yaml", "drg/fragment.yaml");
    }

    @Test
    void loadRefusesWithTheMismatchAndItsDetail() {
        PackContents c =
                contents("rogue", Optional.of(PackLevel.ORG), descriptor("rogue", Optional.of(PackLevel.BUILT_IN)));
        assertThatThrownBy(() -> PackPolicy.load(c, DoctrineGraph.empty()))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_LEVEL_MISMATCH);
                    assertThat(r.getMessage())
                            .isEqualTo("Pack 'rogue' is configured as org but declares pack_level built-in");
                });
    }

    @Test
    void loadBuildsThePackWithTheWarningsAsDiagnostics() {
        PackContents c = contents("qa", Optional.of(PackLevel.ORG), new FileRead.Absent<>("pack.yaml"));
        CharterPack pack = PackPolicy.load(c, DoctrineGraph.empty());
        assertThat(pack.name()).isEqualTo("qa");
        assertThat(pack.level()).isEqualTo(PackLevel.ORG);
        assertThat(pack.descriptor()).isEmpty();
        assertThat(pack.diagnostics())
                .containsExactly(new Diagnostic(
                        Diagnostic.Code.PACK_LEVEL_MISSING,
                        "org pack qa has no pack.yaml; the configured level org is used"));
    }

    @Test
    void loadKeepsTheDescriptorAndHasNoDiagnosticWhenTheLevelsAgree() {
        PackContents c = contents("qa", Optional.of(PackLevel.ORG), descriptor("qa", Optional.of(PackLevel.ORG)));
        CharterPack pack = PackPolicy.load(c, DoctrineGraph.empty());
        assertThat(pack.descriptor()).isPresent();
        assertThat(pack.diagnostics()).isEmpty();
    }

    @Test
    void loadNeedsAConfiguredLevel() {
        PackContents c = contents("qa", Optional.empty(), new FileRead.Absent<>("pack.yaml"));
        assertThatThrownBy(() -> PackPolicy.load(c, DoctrineGraph.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("load needs a configured level");
    }

    // ---- load(contents): the graph comes from the facts -----------------------------------------

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
                List.of());
    }

    private static RawGraph shard(String file, List<PackContents.RawNode> nodes, List<PackContents.RawEdge> edges) {
        return new RawGraph(file, RawGraph.Shape.SHARD, nodes, edges);
    }

    private static PackContents.RawNode node(String urn, String kind) {
        return new PackContents.RawNode(urn, kind, Optional.empty());
    }

    private static PackContents.RawEdge edge(String source, String target, String relation) {
        return new PackContents.RawEdge(source, target, relation, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void loadBuildsTheGraphFromTheShardsThenTheFragment() {
        RawGraph fragment = new RawGraph(
                "drg/fragment.yaml",
                RawGraph.Shape.FRAGMENT,
                List.of(node("f", "tactics")),
                List.of(edge("tactic:f", "tactic:a", "requires")));
        PackContents c = withShard(
                new FileRead.Read<>(
                        "a.graph.yaml", shard("a.graph.yaml", List.of(node("tactic:a", "tactic")), List.of())),
                new FileRead.Read<>("drg/fragment.yaml", fragment),
                descriptor("qa", Optional.of(PackLevel.ORG)));
        CharterPack pack = PackPolicy.load(c);
        assertThat(pack.graph().nodes()).extracting(n -> n.urn().toString()).containsExactly("tactic:a", "tactic:f");
        assertThat(pack.graph().edges()).hasSize(1);
        assertThat(pack.diagnostics()).isEmpty();
    }

    @Test
    void loadKeepsTheWarningsAsDiagnosticsLikeTheTransitionalOverload() {
        PackContents c = withShard(
                new FileRead.Read<>("a.graph.yaml", shard("a.graph.yaml", List.of(), List.of())),
                new FileRead.Absent<>("drg/fragment.yaml"),
                new FileRead.Absent<>("pack.yaml"));
        assertThat(PackPolicy.load(c).diagnostics())
                .extracting(Diagnostic::code)
                .containsExactly(Diagnostic.Code.PACK_LEVEL_MISSING);
    }

    @Test
    void loadRefusesWithTheFirstGraphFindingBeforeBuildingAnything() {
        PackContents c = withShard(
                new FileRead.Read<>(
                        "a.graph.yaml",
                        shard("a.graph.yaml", List.of(), List.of(edge("tactic:a", "QA_404", "requires")))),
                new FileRead.Absent<>("drg/fragment.yaml"),
                descriptor("qa", Optional.of(PackLevel.ORG)));
        assertThatThrownBy(() -> PackPolicy.load(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
            assertThat(r.getMessage()).isEqualTo("qa/a.graph.yaml: edge endpoint 'QA_404' is not a URN");
        });
    }

    @Test
    void loadRefusesAnUnreadableDescriptorWithItsOwnTextAndCode() {
        PackContents c = withShard(
                new FileRead.Read<>("a.graph.yaml", shard("a.graph.yaml", List.of(), List.of())),
                new FileRead.Absent<>("drg/fragment.yaml"),
                new FileRead.Unreadable<>(
                        "pack.yaml", new PackContents.ParseFailure("pack.yaml", "while scanning"), List.of()));
        assertThatThrownBy(() -> PackPolicy.load(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(r.getMessage()).isEqualTo("pack.yaml of qa is not valid YAML");
        });
        assertThatThrownBy(() -> PackPolicy.load(c, DoctrineGraph.empty()))
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
                new FileRead.Absent<>("drg/fragment.yaml"),
                descriptor("qa", Optional.of(PackLevel.ORG)));
        assertThatThrownBy(() -> PackPolicy.load(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
            assertThat(r.getMessage()).isEqualTo("qa/a.graph.yaml is not valid YAML: while parsing a block mapping");
        });
    }

    @Test
    void anUnreadableDescriptorWithNeitherAParseFailureNorViolationsStillRefuses() {
        PackContents c = withShard(
                new FileRead.Read<>("a.graph.yaml", shard("a.graph.yaml", List.of(), List.of())),
                new FileRead.Absent<>("drg/fragment.yaml"),
                new FileRead.Unreadable<>("pack.yaml", null, List.of()));
        assertThatThrownBy(() -> PackPolicy.load(c)).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(r.getMessage()).isEqualTo("pack.yaml of qa is unreadable");
        });
    }

    @Test
    void loadWithoutAConfiguredLevelIsAProgrammingError() {
        PackContents c = contents("qa", Optional.empty(), new FileRead.Absent<>("pack.yaml"));
        assertThatThrownBy(() -> PackPolicy.load(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("load needs a configured level");
    }
}
