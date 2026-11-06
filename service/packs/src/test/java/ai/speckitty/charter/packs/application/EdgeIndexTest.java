package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The edge-backed resolver (WP11 T063): the typed edges of a closure's facts, indexed by (holding pack, source URN), so
 * a {@link GraphWalk} reaches every graph-authored kind from the facts the compose already read. Packs are named by
 * their descriptor {@code pack_id} ({@code id-<name>}), the identity the {@link ClosureView} uses.
 */
class EdgeIndexTest {

    /** A hand-built closure: each pack with what it holds and the edges its facts carry. */
    static final class Closure {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new HashMap<>();

        /**
         * Adds a pack, depending on the named packs.
         *
         * @param name the configured identifier and descriptor name; the descriptor {@code pack_id} is {@code id-name}
         * @param dependsOn the names of the packs it depends on
         * @param held the bare URNs it holds
         * @param edges the edges its facts carry
         * @return this closure
         */
        Closure pack(String name, List<String> dependsOn, List<String> held, Relationship... edges) {
            List<PackDependency> dependencies = dependsOn.stream()
                    .map(other -> new PackDependency(packId(other), ">=0"))
                    .toList();
            PackDescriptor descriptor = new PackDescriptor(
                    packId(name),
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    dependencies);
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG), descriptor, List.of(), held));
            facts.put(
                    name,
                    new PackCompositionFacts(
                            List.of(),
                            held,
                            List.of(),
                            MutabilityPolicySource.empty(),
                            List.of(),
                            Arrays.asList(edges)));
            return this;
        }

        Closure pack(String name, List<String> held, Relationship... edges) {
            return pack(name, List.of(), held, edges);
        }

        EdgeIndex index() {
            return EdgeIndex.of(packs, facts);
        }
    }

    static String packId(String name) {
        return "id-" + name;
    }

    static PackIdentifier pack(String name) {
        return new PackIdentifier(packId(name));
    }

    static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    /** An edge as written: either endpoint may carry a {@code <pack>::} qualifier. */
    static Relationship edge(String from, RelationType type, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    private static List<String> written(CharterArtifact node) {
        return node.relationships().stream()
                .map(relationship ->
                        relationship.from() + " " + relationship.type().token() + " " + relationship.toReference())
                .toList();
    }

    @Test
    void aHeldSourceResolvesToANodeCarryingItsIndexedEdgesInThatPack() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:T", "agent_profile:P", "agent_profile:Q"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"),
                        edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "agent_profile:Q"))
                .index();

        CharterArtifact directive =
                index.inPack(pack("alpha")).resolve(urn("directive:D")).orElseThrow();
        assertThat(directive.packId()).isEqualTo(pack("alpha"));
        assertThat(directive.urn()).isEqualTo(urn("directive:D"));
        assertThat(directive.label()).isEqualTo("directive:D");
        assertThat(written(directive)).containsExactly("directive:D requires tactic:T");
        assertThat(written(index.inPack(pack("alpha"))
                        .resolve(urn("agent_profile:P"))
                        .orElseThrow()))
                .as("every graph-authored kind resolves, not only the five the component resolver dispatches")
                .containsExactly("agent_profile:P specializes_from agent_profile:Q");
        assertThat(index.inPack(pack("alpha"))
                        .resolve(urn("tactic:T"))
                        .orElseThrow()
                        .relationships())
                .as("a held artifact with no edges is still a node")
                .isEmpty();
    }

    @Test
    void aPackThatDoesNotHoldTheUrnResolvesNothing() {
        EdgeIndex index = new Closure()
                .pack("alpha", List.of("directive:D"), edge("directive:D", RelationType.REQUIRES, "tactic:T"))
                .pack("beta", List.of("tactic:T"))
                .index();

        assertThat(index.inPack(pack("alpha")).resolve(urn("tactic:T"))).isEmpty();
        assertThat(index.inPack(pack("beta")).resolve(urn("directive:D"))).isEmpty();
        assertThat(index.inPack(new PackIdentifier("id-nowhere")).resolve(urn("directive:D")))
                .isEmpty();
    }

    @Test
    void twoPacksHoldingOneUrnEachKeepTheirOwnEdges() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:T1"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T1"))
                .pack(
                        "beta",
                        List.of("directive:D", "tactic:T2"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T2"))
                .index();

        assertThat(written(
                        index.inPack(pack("alpha")).resolve(urn("directive:D")).orElseThrow()))
                .containsExactly("directive:D requires tactic:T1");
        assertThat(written(
                        index.inPack(pack("beta")).resolve(urn("directive:D")).orElseThrow()))
                .containsExactly("directive:D requires tactic:T2");
    }

    @Test
    void aQualifiedReferenceIsHeldByThePackOfThatNameByTheComposesRule() {
        EdgeIndex index = new Closure()
                .pack("beta", List.of("tactic:T"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("directive:D"),
                        edge("directive:D", RelationType.REQUIRES, "beta::tactic:T"))
                .index();

        assertThat(index.holdingPack(PackQualifiedReference.parse("beta::tactic:T"), pack("alpha")))
                .contains(pack("beta"));
        assertThat(index.holdingPack(PackQualifiedReference.parse("gamma::tactic:T"), pack("alpha")))
                .isEmpty();
        assertThat(index.holdingPack(PackQualifiedReference.parse("directive:D"), pack("alpha")))
                .as("a bare reference is held by the referring pack when it holds it")
                .contains(pack("alpha"));
    }

    @Test
    void aQualifiedTargetOutsideTheWritersDependsOnClosureIsRefusedAsTheComposeRefusesIt() {
        Closure closure = new Closure()
                .pack("beta", List.of("tactic:T"))
                .pack("alpha", List.of("directive:D"), edge("directive:D", RelationType.REQUIRES, "beta::tactic:T"));

        assertThatThrownBy(closure::index)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING));
    }

    @Test
    void anEdgeWrittenFromAnotherPacksArtifactIsHeldThereAndKeepsItsBareTargetInTheWriter() {
        EdgeIndex index = new Closure()
                .pack("beta", List.of("directive:B"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("tactic:T"),
                        edge("beta::directive:B", RelationType.REQUIRES, "tactic:T"))
                .index();

        assertThat(written(
                        index.inPack(pack("beta")).resolve(urn("directive:B")).orElseThrow()))
                .containsExactly("directive:B requires alpha::tactic:T");
    }

    @Test
    void theReverseIndexNamesEachSourceOfARelationIntoAUrnWithItsPack() {
        EdgeIndex index = new Closure()
                .pack("beta", List.of("tactic:T"), edge("tactic:T", RelationType.REQUIRES, "tactic:U"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("tactic:E", "tactic:F"),
                        edge("tactic:E", RelationType.ENHANCES, "beta::tactic:T"),
                        edge("tactic:F", RelationType.REQUIRES, "beta::tactic:T"))
                .index();

        assertThat(index.incoming(urn("tactic:T"), RelationType.ENHANCES))
                .containsExactly(new EdgeIndex.Place(pack("alpha"), urn("tactic:E")));
        assertThat(index.incoming(urn("tactic:T"), RelationType.REQUIRES))
                .containsExactly(new EdgeIndex.Place(pack("alpha"), urn("tactic:F")));
        assertThat(index.incoming(urn("tactic:E"), RelationType.ENHANCES)).isEmpty();
    }
}
