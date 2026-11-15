package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.Directionality;
import ai.speckitty.charter.kernel.domain.EndpointPair;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Tension;
import ai.speckitty.charter.packs.domain.PackPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The kind taxonomy (R18, #112): every node kind with its facts and every relation with the kinds it may connect. It is
 * the model's, not the configured packs': every fact is read from the kernel's kinds and relations ({@link NodeKind},
 * {@link ArtifactKind}, {@link RelationType} and {@link Tension}), never listed here, so a kind or a relation the model
 * gains or loses reaches the answer with no change to this class. It reads no pack file.
 *
 * <p>The kinds are in {@link NodeKind} order. The relations are in {@link RelationType} order, then the tension, which
 * the model holds beside the relationships (ADR-0028, ADR-0045) and which the pack files write
 * {@value PackPolicy#TENSION}. The endpoints of a relation are sorted by source kind, then by target kind, in
 * {@link NodeKind} order, so the answer is the same on every run.
 */
@Service
public final class KindTaxonomyReadService {

    /** How the service holds an edge of a relation. */
    public enum HeldAs {
        /** A {@code Relationship} of a {@link RelationType}. */
        RELATIONSHIP,
        /** A {@link Tension}, held beside the relationships. */
        TENSION;

        /**
         * The wire spelling.
         *
         * @return the lower-case constant name
         */
        public String token() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One node kind and its facts.
     *
     * @param kind the kind
     * @param plural its plural; empty for a kind that has none
     * @param artifactKind the artifact kind it stands for; empty for a kind that is no artifact kind
     * @param artifact whether it is an artifact kind
     * @param activatable whether an activation selects, denies or requires it
     * @param declarableInGraphs whether a graph document may declare a node of it
     * @param edgeTarget whether an authored edge may name it as its target
     * @param listed whether the pack artifact list serves it, so a row links that list
     */
    public record KindFacts(
            NodeKind kind,
            Optional<String> plural,
            Optional<ArtifactKind> artifactKind,
            boolean artifact,
            boolean activatable,
            boolean declarableInGraphs,
            boolean edgeTarget,
            boolean listed) {}

    /**
     * One relation and the kinds it may connect.
     *
     * @param relation the relation's token
     * @param heldAs how the service holds an edge of it
     * @param directed whether it holds from source to target only
     * @param derived whether it is only ever derived, never authored
     * @param endpoints the allowed source and target kinds, sorted by source, then target, in kind order
     */
    public record RelationFacts(
            String relation, HeldAs heldAs, boolean directed, boolean derived, List<EndpointPair> endpoints) {

        /** Copies the endpoints. */
        public RelationFacts {
            endpoints = List.copyOf(endpoints);
        }
    }

    /**
     * The taxonomy.
     *
     * @param kinds every node kind, in {@link NodeKind} order
     * @param relations every relation, in {@link RelationType} order, then the tension
     */
    public record Taxonomy(List<KindFacts> kinds, List<RelationFacts> relations) {

        /** Copies the lists. */
        public Taxonomy {
            kinds = List.copyOf(kinds);
            relations = List.copyOf(relations);
        }
    }

    private static final Comparator<EndpointPair> KIND_ORDER =
            Comparator.comparing(EndpointPair::source).thenComparing(EndpointPair::target);

    private final Taxonomy taxonomy = build();

    /** The read; it holds the taxonomy, built once. */
    public KindTaxonomyReadService() {}

    /**
     * The kind taxonomy.
     *
     * @return every kind and every relation, in model order
     */
    public Taxonomy taxonomy() {
        return taxonomy;
    }

    private static Taxonomy build() {
        List<KindFacts> kinds = Arrays.stream(NodeKind.values())
                .map(KindTaxonomyReadService::kind)
                .toList();
        List<RelationFacts> relations = new ArrayList<>();
        for (RelationType type : RelationType.values()) {
            relations.add(new RelationFacts(
                    type.token(),
                    HeldAs.RELATIONSHIP,
                    type.directionality() == Directionality.DIRECTED,
                    type.derived(),
                    sorted(type.endpoints())));
        }
        relations.add(new RelationFacts(
                PackPolicy.TENSION,
                HeldAs.TENSION,
                Tension.directionality() == Directionality.DIRECTED,
                Tension.derived(),
                sorted(Tension.allowedEndpoints())));
        return new Taxonomy(kinds, relations);
    }

    private static KindFacts kind(NodeKind kind) {
        return new KindFacts(
                kind,
                kind.hasPlural() ? Optional.of(kind.plural()) : Optional.empty(),
                kind.artifactKind(),
                kind.artifactKind().isPresent(),
                kind.artifactKind().map(ArtifactKind::activatable).orElse(false),
                kind.declarableInGraphs(),
                kind.edgeTarget(),
                kind.artifactKind()
                        .filter(ArtifactReadService.listed()::contains)
                        .isPresent());
    }

    private static List<EndpointPair> sorted(Set<EndpointPair> pairs) {
        return pairs.stream().sorted(KIND_ORDER).toList();
    }
}
