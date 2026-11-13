package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.ArtifactSummary;
import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.DenyNotApplied;
import ai.speckitty.charter.api.model.EffectiveArtifact;
import ai.speckitty.charter.api.model.EffectiveGraphEdge;
import ai.speckitty.charter.api.model.EffectiveGraphNode;
import ai.speckitty.charter.api.model.EnhancerWithoutTarget;
import ai.speckitty.charter.api.model.LayerOverrideSide;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.NodeReference;
import ai.speckitty.charter.api.model.OfferingPack;
import ai.speckitty.charter.api.model.OverriddenArtifact;
import ai.speckitty.charter.api.model.RequiredNotInForce;
import ai.speckitty.charter.api.model.SelectedArtifacts;
import ai.speckitty.charter.api.model.SharedSkip;
import ai.speckitty.charter.api.model.SkippedDeactivated;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.domain.Diagnostic;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Every domain enum that crosses the wire spells its values exactly as the contract enum does: each domain
 * token is a value of the contract enum, and the contract enum has no value the domain lacks.
 */
class WireTokenParityTest {

    /**
     * One domain enum against its contract enum. {@code retiredContractOnly} names the contract values the domain
     * deliberately no longer has, each with the reason the contract keeps it; it is empty for every pair but one.
     */
    private record Pair(
            String contractEnum,
            List<String> domainTokens,
            List<String> contractValues,
            Function<String, ?> fromValue,
            Map<String, String> retiredContractOnly) {

        Pair(
                String contractEnum,
                List<String> domainTokens,
                List<String> contractValues,
                Function<String, ?> fromValue) {
            this(contractEnum, domainTokens, contractValues, fromValue, Map.of());
        }
    }

    /**
     * The contract values the domain retired and the contract keeps so a client built against an earlier draft still
     * reads them. {@code pack_unknown_key} was once raised as a load warning; the pack descriptor schema has been
     * closed since ADR-0036 decision 3, so such a pack is refused as {@code pack_unreadable}, and ADR-0042 removed the
     * constant. {@code PackPolicyRulesTest} asserts it stays absent from the domain, so it must stay out of
     * {@code Diagnostic.Code}.
     */
    private static final Map<String, String> RETIRED_DIAGNOSTIC_CODES = Map.of(
            "pack_unknown_key",
            "retired and never raised: the closed pack descriptor schema refuses such a pack (ADR-0036 decision 3,"
                    + " ADR-0042); the contract keeps the value for clients built against an earlier draft");

    /** The values of the contract's {@code DiagnosticCode} enum, which no path references, so the generator skips it. */
    private static final List<String> DIAGNOSTIC_CODES = contractEnum("DiagnosticCode.yaml");

    /**
     * The node kinds a graph document or relationship registration may name. The contract's {@code NodeKind} lists
     * these and no more: {@code guard} and {@code lifecycle} are in the domain but are not authored in graphs and stay
     * out of the contract until their contract set lands (operator ruling of 2026-11-02, #156), so the domain side is
     * filtered by {@link NodeKind#authoredInGraphs()}.
     */
    private static final List<String> AUTHORED_NODE_KINDS = Arrays.stream(NodeKind.values())
            .filter(NodeKind::authoredInGraphs)
            .map(NodeKind::token)
            .toList();

    /** The values of the contract's {@code ArtifactKindToken} enum, the {@code ?kind=} filter's. */
    private static final List<String> ARTIFACT_KIND_TOKENS = artifactKindTokens();

    /** The activatable kinds of the one kind registry, in declaration order (IC-20). */
    private static final List<String> ACTIVATABLE = Arrays.stream(ArtifactKind.values())
            .filter(ArtifactKind::activatable)
            .map(ArtifactKind::token)
            .toList();

    /** The members of the generated {@code SelectedArtifacts}: one JSON property per activatable kind. */
    private static final List<String> SELECTED_MEMBERS = Arrays.stream(SelectedArtifacts.class.getDeclaredMethods())
            .filter(m -> m.getName().startsWith("get"))
            .map(m -> m.getAnnotation(JsonProperty.class))
            .filter(Objects::nonNull)
            .map(JsonProperty::value)
            .toList();

    private static List<Pair> pairs() {
        return Stream.of(activatableKindCopies(), inlinedEnumAndTokenPairs(), inlinedGraphLevelAndRelationPairs())
                .flatMap(List::stream)
                .toList();
    }

    /** Every copy of ActivatableKind the generator inlines, each a pair of its own against the activatable kinds. */
    private static List<Pair> activatableKindCopies() {
        return List.of(
                new Pair(
                        "ArtifactReference.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(ArtifactReference.KindEnum.values(), ArtifactReference.KindEnum::getValue),
                        ArtifactReference.KindEnum::fromValue),
                new Pair(
                        "EffectiveArtifact.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(EffectiveArtifact.KindEnum.values(), EffectiveArtifact.KindEnum::getValue),
                        EffectiveArtifact.KindEnum::fromValue),
                new Pair(
                        "SharedSkip.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(SharedSkip.KindEnum.values(), SharedSkip.KindEnum::getValue),
                        SharedSkip.KindEnum::fromValue),
                new Pair(
                        "SkippedDeactivated.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(SkippedDeactivated.KindEnum.values(), SkippedDeactivated.KindEnum::getValue),
                        SkippedDeactivated.KindEnum::fromValue),
                // The four inlined copies the first four rows leave out, so every copy of ActivatableKind is paired.
                new Pair(
                        "DenyNotApplied.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(DenyNotApplied.KindEnum.values(), DenyNotApplied.KindEnum::getValue),
                        DenyNotApplied.KindEnum::fromValue),
                new Pair(
                        "EnhancerWithoutTarget.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(EnhancerWithoutTarget.KindEnum.values(), EnhancerWithoutTarget.KindEnum::getValue),
                        EnhancerWithoutTarget.KindEnum::fromValue),
                new Pair(
                        "RequiredNotInForce.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(RequiredNotInForce.KindEnum.values(), RequiredNotInForce.KindEnum::getValue),
                        RequiredNotInForce.KindEnum::fromValue),
                new Pair(
                        "OverriddenArtifact.kind (ActivatableKind)",
                        ACTIVATABLE,
                        values(OverriddenArtifact.KindEnum.values(), OverriddenArtifact.KindEnum::getValue),
                        OverriddenArtifact.KindEnum::fromValue));
    }

    /** The pairs that are not copies of ActivatableKind: the node reference, selected members, refusal codes and kinds. */
    private static List<Pair> inlinedEnumAndTokenPairs() {
        return List.of(
                // NodeReference.kind is a copy of NodeKind the generator inlines; the targets of an enhancer without a
                // target in force are the one place it reaches the wire before the graph is served.
                new Pair(
                        "NodeReference.kind (NodeKind)",
                        AUTHORED_NODE_KINDS,
                        values(NodeReference.KindEnum.values(), NodeReference.KindEnum::getValue),
                        NodeReference.KindEnum::fromValue),
                new Pair(
                        "SelectedArtifacts members",
                        ACTIVATABLE,
                        SELECTED_MEMBERS,
                        token -> member(SELECTED_MEMBERS, token)),
                new Pair(
                        "CharterRefusalCode",
                        tokens(RefusalCode.values(), RefusalCode::token),
                        values(CharterRefusal.CodeEnum.values(), CharterRefusal.CodeEnum::getValue),
                        CharterRefusal.CodeEnum::fromValue),
                // The listed kinds against ArtifactKindToken, which only the ?kind= filter references (#99: the kinds
                // the filter takes are the served ones, asset and skill among them since #105 and #107, then
                // mission_type and mission_step_contract). The generator
                // types that parameter as a String, so the enum is read from the contract file itself.
                new Pair(
                        "ArtifactKindToken",
                        tokens(ArtifactReadService.LISTED.toArray(ArtifactKind[]::new), ArtifactKind::token),
                        ARTIFACT_KIND_TOKENS,
                        token -> member(ARTIFACT_KIND_TOKENS, token)));
    }

    /** The graph-facing copies: NodeKind, the diagnostic codes, the pack levels and the relation of an edge. */
    private static List<Pair> inlinedGraphLevelAndRelationPairs() {
        return List.of(
                // NodeKind reaches the wire through EffectiveGraphNode.kind (the effective graph, served since PR C)
                // and
                // NodeReference.kind (below); the domain side is the kinds authored in graphs (see AUTHORED_NODE_KINDS
                // for
                // why guard and lifecycle are not among them).
                new Pair(
                        "NodeKind",
                        AUTHORED_NODE_KINDS,
                        values(EffectiveGraphNode.KindEnum.values(), EffectiveGraphNode.KindEnum::getValue),
                        EffectiveGraphNode.KindEnum::fromValue),
                // DiagnosticCode against Diagnostic.Code, less the retired value the contract keeps.
                new Pair(
                        "DiagnosticCode",
                        tokens(Diagnostic.Code.values(), Diagnostic.Code::token),
                        DIAGNOSTIC_CODES,
                        token -> member(DIAGNOSTIC_CODES, token),
                        RETIRED_DIAGNOSTIC_CODES),
                // The pack levels against PackLevel, as the generator types it on OfferingPack.level.
                new Pair(
                        "PackLevel",
                        tokens(PackLevel.values(), PackLevel::token),
                        values(OfferingPack.LevelEnum.values(), OfferingPack.LevelEnum::getValue),
                        OfferingPack.LevelEnum::fromValue),
                // The two copies the generator inlines for the effective graph (IC-11): the level of a layer override's
                // version and the relation of an edge, which ClosureViewSession's callers read by fromValue.
                new Pair(
                        "LayerOverrideSide.level (PackLevel)",
                        tokens(PackLevel.values(), PackLevel::token),
                        values(LayerOverrideSide.LevelEnum.values(), LayerOverrideSide.LevelEnum::getValue),
                        LayerOverrideSide.LevelEnum::fromValue),
                new Pair(
                        "EffectiveGraphEdge.relation (RelationType)",
                        // Less implemented_by, a guard's derived edge: its source is a guard, which is no node of the
                        // graph
                        // (no activation state, decision 01M702A71YF7WNDTG2AA89K8ZA), so no edge of the graph carries
                        // it.
                        tokens(RelationType.values(), RelationType::token).stream()
                                .filter(token -> !token.equals("implemented_by"))
                                .toList(),
                        values(EffectiveGraphEdge.RelationEnum.values(), EffectiveGraphEdge.RelationEnum::getValue),
                        EffectiveGraphEdge.RelationEnum::fromValue));
    }

    private static List<String> artifactKindTokens() {
        return contractEnum("ArtifactKindToken.yaml");
    }

    private static List<String> contractEnum(String schemaFile) {
        Path file = Path.of(System.getProperty("charter.contractSchemas"), schemaFile);
        try {
            Map<?, ?> document = (Map<?, ?>) new Load(LoadSettings.builder().build())
                    .loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            return ((List<?>) document.get("enum"))
                    .stream().map(String::valueOf).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }

    private static String member(List<String> values, String token) {
        if (!values.contains(token)) {
            throw new IllegalArgumentException(token + " is not one of " + values);
        }
        return token;
    }

    private static <E> List<String> tokens(E[] constants, Function<E, String> token) {
        return Arrays.stream(constants).map(token).toList();
    }

    private static <E> List<String> values(E[] constants, Function<E, String> value) {
        return tokens(constants, value);
    }

    /** The parity check of one pair: every domain token is a contract value, and there is no other value. */
    private static void assertParity(Pair pair) {
        for (String token : pair.domainTokens()) {
            assertThatCode(() -> pair.fromValue().apply(token))
                    .as(pair.contractEnum() + " accepts " + token)
                    .doesNotThrowAnyException();
        }
        assertThat(pair.retiredContractOnly().keySet())
                .as(pair.contractEnum() + " retired values are still in the contract")
                .isSubsetOf(pair.contractValues());
        assertThat(Collections.disjoint(
                        pair.domainTokens(), pair.retiredContractOnly().keySet()))
                .as(pair.contractEnum() + " retired values are not in the domain")
                .isTrue();
        assertThat(pair.contractValues().stream()
                        .filter(value -> !pair.retiredContractOnly().containsKey(value))
                        .toList())
                .as(pair.contractEnum() + " values")
                .containsExactlyInAnyOrderElementsOf(pair.domainTokens());
    }

    private static Pair withTokens(Pair pair, List<String> tokens) {
        return new Pair(
                pair.contractEnum(), tokens, pair.contractValues(), pair.fromValue(), pair.retiredContractOnly());
    }

    @TestFactory
    Stream<DynamicTest> everyDomainTokenIsAContractValueAndNoMore() {
        return pairs().stream().map(pair -> dynamicTest(pair.contractEnum(), () -> assertParity(pair)));
    }

    @Test
    void theActivatableKindsAreTheContractsActivatableKindInItsOrder() {
        assertThat(values(ArtifactReference.KindEnum.values(), ArtifactReference.KindEnum::getValue))
                .containsExactlyElementsOf(ACTIVATABLE);
    }

    @Test
    void aResponseKindIsAnOpenSlugSoAClientToleratesAKindItDoesNotKnow() throws NoSuchMethodException {
        // ArtifactSummary.kind and Artifact.kind are open slugs, not ArtifactKindToken: a kind a later version serves
        // (glossary, procedure) must not break a client generated from this one.
        for (Class<?> model : List.of(ArtifactSummary.class, Artifact.class)) {
            assertThat(model.getMethod("getKind").getReturnType())
                    .as(model.getSimpleName() + ".kind")
                    .isEqualTo(String.class);
            Pattern pattern = model.getMethod("getKind").getAnnotation(Pattern.class);
            assertThat(pattern).as(model.getSimpleName() + ".kind's @Pattern").isNotNull();
            assertThat("glossary").matches(pattern.regexp());
            assertThat("Tactic").doesNotMatch(pattern.regexp());
        }
        assertThat(new ArtifactSummary("glossary", "g", "G", Map.<String, Link>of()).getKind())
                .isEqualTo("glossary");
    }

    @Test
    void anExemptionFailsWhenItNamesAValueTheContractNoLongerHasOrTheDomainHasAgain() {
        Pair diagnostics = pairs().stream()
                .filter(pair -> pair.contractEnum().equals("DiagnosticCode"))
                .findFirst()
                .orElseThrow();
        assertThat(diagnostics.retiredContractOnly()).containsOnlyKeys("pack_unknown_key");
        // The contract drops the retired value while the exemption stays: only the subset guard can catch it, since
        // the remaining values still equal the domain's.
        List<String> withoutRetired = diagnostics.contractValues().stream()
                .filter(value -> !value.equals("pack_unknown_key"))
                .toList();
        Pair stale = new Pair(
                diagnostics.contractEnum(),
                diagnostics.domainTokens(),
                withoutRetired,
                diagnostics.fromValue(),
                diagnostics.retiredContractOnly());
        assertThatThrownBy(() -> assertParity(stale))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("retired values are still in the contract");
        // The domain gains the retired constant back: the disjointness guard catches it.
        List<String> revived = Stream.concat(diagnostics.domainTokens().stream(), Stream.of("pack_unknown_key"))
                .toList();
        assertThatThrownBy(() -> assertParity(withTokens(diagnostics, revived)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("retired values are not in the domain");
    }

    @Test
    void onlyGuardAndLifecycleAreLeftOutOfTheNodeKindRow() {
        // The NodeKind row compares only the kinds authored in graphs. A new kind the filter leaves out must be a
        // deliberate choice made here, not a gap the row cannot see.
        assertThat(Arrays.stream(NodeKind.values())
                        .map(NodeKind::token)
                        .filter(token -> !AUTHORED_NODE_KINDS.contains(token))
                        .toList())
                .containsExactlyInAnyOrder("guard", "lifecycle");
    }

    @TestFactory
    Stream<DynamicTest> theParityCheckFailsOnASkewedPair() {
        // Positive control for every pair: the same check fails when the domain has a token the contract lacks,
        // and when the contract has a value the domain lacks.
        return pairs().stream()
                .map(pair -> dynamicTest(pair.contractEnum(), () -> {
                    List<String> extra = Stream.concat(pair.domainTokens().stream(), Stream.of("not_a_contract_value"))
                            .toList();
                    assertThatThrownBy(() -> assertParity(withTokens(pair, extra)))
                            .isInstanceOf(AssertionError.class);
                    List<String> missing =
                            pair.domainTokens().subList(1, pair.domainTokens().size());
                    assertThatThrownBy(() -> assertParity(withTokens(pair, missing)))
                            .isInstanceOf(AssertionError.class);
                }));
    }
}
