package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The rule of FR-014 (operator ruling of 2026-11-12 on #447, option 1): an {@code overrides} edge whose source is not of
 * an activatable kind is refused. Every refused edge has a twin that passes and differs only in the source kind.
 */
class OverrideSourceRuleTest {

    private static final String FILE = "overrides.graph.yaml";
    private static final String TARGET = "base::directive:D";

    private static OverrideSourceRule.Edge overrides(String source) {
        return new OverrideSourceRule.Edge(FILE, source, TARGET, RelationType.OVERRIDES.token());
    }

    private static String source(ArtifactKind kind) {
        return NodeKind.of(kind).token() + ":t";
    }

    @ParameterizedTest
    @EnumSource(
            value = ArtifactKind.class,
            names = {"TEMPLATE", "ASSET", "GUARD", "LIFECYCLE"})
    void anOverridesEdgeFromEachKindThatIsNotActivatableIsOneFaultAtItsFile(ArtifactKind kind) {
        List<OverrideSourceRule.Fault> faults = OverrideSourceRule.judge(List.of(overrides(source(kind))));

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.file()).isEqualTo(FILE);
            assertThat(fault.reason()).contains(source(kind)).contains(TARGET).contains("not an activatable kind");
        });
    }

    @ParameterizedTest
    @EnumSource(
            value = ArtifactKind.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"TEMPLATE", "ASSET", "GUARD", "LIFECYCLE"})
    void anOverridesEdgeFromEachActivatableKindPasses(ArtifactKind kind) {
        assertThat(kind.activatable()).isTrue();

        assertThat(OverrideSourceRule.judge(List.of(overrides(source(kind))))).isEmpty();
    }

    @Test
    void theFourKindsTheRuleRefusesAreExactlyTheKindsThatAreNotActivatable() {
        assertThat(ArtifactKind.values())
                .filteredOn(kind -> !kind.activatable())
                .containsExactlyInAnyOrder(
                        ArtifactKind.TEMPLATE, ArtifactKind.ASSET, ArtifactKind.GUARD, ArtifactKind.LIFECYCLE);
    }

    @Test
    void aSourceQualifiedWithAPackNameIsJudgedLikeABareOne() {
        assertThat(OverrideSourceRule.judge(List.of(overrides("main::template:t.md"))))
                .singleElement()
                .satisfies(fault -> assertThat(fault.reason()).contains("main::template:t.md"));
        assertThat(OverrideSourceRule.judge(List.of(overrides("main::directive:E"))))
                .isEmpty();
    }

    @Test
    void anEdgeOfAnotherRelationFromATemplatePasses() {
        for (RelationType relation : RelationType.values()) {
            if (relation == RelationType.OVERRIDES) {
                continue;
            }
            assertThat(OverrideSourceRule.judge(
                            List.of(new OverrideSourceRule.Edge(FILE, "template:t.md", TARGET, relation.token()))))
                    .as(relation.token())
                    .isEmpty();
        }
    }

    @Test
    void theRuleJudgesTheSourceOnlySoAnOverridesEdgeToATemplatePasses() {
        assertThat(OverrideSourceRule.judge(
                        List.of(new OverrideSourceRule.Edge(FILE, "directive:E", "template:t.md", "overrides"))))
                .isEmpty();
    }

    @Test
    void anEndpointThatIsNoUrnIsLeftToTheGraphRules() {
        assertThat(OverrideSourceRule.judge(List.of(overrides("not a urn")))).isEmpty();
    }

    @Test
    void everyRefusedEdgeIsItsOwnFaultInEdgeOrder() {
        List<OverrideSourceRule.Fault> faults = OverrideSourceRule.judge(List.of(
                overrides("template:t.md"),
                overrides("directive:E"),
                new OverrideSourceRule.Edge("other.graph.yaml", "asset:logo", TARGET, "overrides")));

        assertThat(faults).extracting(OverrideSourceRule.Fault::file).containsExactly(FILE, "other.graph.yaml");
        assertThat(faults.get(0).reason()).contains("template:t.md");
        assertThat(faults.get(1).reason()).contains("asset:logo");
    }

    @Test
    void aFaultIsALoadViolationOfItsFileUnderTheViolationCode() {
        OverrideSourceRule.Fault fault =
                OverrideSourceRule.judge(List.of(overrides("template:t.md"))).getFirst();

        assertThat(OverrideSourceRule.violationOf(fault))
                .isEqualTo(new PackElementViolation(FILE, "overrides_source_not_activatable", fault.reason()));
        assertThat(OverrideSourceRule.CODE).isEqualTo("overrides_source_not_activatable");
    }

    @Test
    void aRelationshipOverridingFromATemplateIsRefusedAndItsTwinFromADirectivePasses() {
        Urn directive = new Urn(NodeKind.DIRECTIVE, "D");

        assertThat(OverrideSourceRule.refusal(
                        Relationship.of(new Urn(NodeKind.TEMPLATE, "t.md"), directive, RelationType.OVERRIDES)))
                .hasValueSatisfying(reason -> assertThat(reason).contains("template:t.md"));
        assertThat(OverrideSourceRule.refusal(
                        Relationship.of(new Urn(NodeKind.DIRECTIVE, "E"), directive, RelationType.OVERRIDES)))
                .isEmpty();
        assertThat(OverrideSourceRule.refusal(
                        Relationship.of(new Urn(NodeKind.TEMPLATE, "t.md"), directive, RelationType.REQUIRES)))
                .isEmpty();
    }
    // ---- validate mode agrees with the load (ADR-0042 decision 7) -------------------------------------------------

    private static PackContents validated(String source) {
        RawGraph shard = new RawGraph(
                FILE,
                RawGraph.Shape.SHARD,
                List.of(),
                List.of(new RawEdge(
                        source, TARGET, "overrides", Optional.empty(), Optional.empty(), Optional.empty())));
        return new PackContents(
                new PackRoot("main", Optional.of(PackLevel.ORG), "loc"),
                new FileRead.Read<>(
                        "pack.yaml",
                        new PackDescriptor(
                                "01ARWG13C00000000000001ABC",
                                "1.0.0",
                                Optional.empty(),
                                "main",
                                Optional.of(PackLevel.ORG),
                                Optional.empty())),
                List.of(new FileRead.Read<>(FILE, shard)),
                new FileRead.Read<>(
                        "drg/fragment.yaml",
                        new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void validateModeReportsTheEdgeAsOneBlockingFindingAtItsGraphDocumentAndTheTwinPasses() {
        List<PackFinding> findings =
                PackPolicy.judge(validated("template:t.md")).findings();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.OVERRIDES_SOURCE_NOT_ACTIVATABLE);
            assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
            assertThat(finding.file()).isEqualTo(FILE);
            assertThat(finding.place()).containsExactly("edges", "0");
            assertThat(finding.detail()).startsWith("main/" + FILE + ": ").contains("template:t.md");
        });
        assertThat(PackPolicy.judge(validated("directive:E")).findings()).isEmpty();
    }

    @Test
    void theFindingCodeAndTheViolationCodeAreOneToken() {
        assertThat(PackPolicy.violationKind(PackFindingCode.OVERRIDES_SOURCE_NOT_ACTIVATABLE))
                .isEqualTo(OverrideSourceRule.CODE);
        assertThat(PackFindingCode.OVERRIDES_SOURCE_NOT_ACTIVATABLE.name().toLowerCase(Locale.ROOT))
                .isEqualTo(OverrideSourceRule.CODE);
    }
}
