package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmReference;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The US2 traversal half (SC-002, research D6): a {@link ComponentResolver} that resolves {@code directive:} URNs via
 * {@link DirectiveReadService} (WP05) and {@code paradigm:} URNs via {@link ParadigmReadService} (this slice) drives a
 * {@link GraphWalk} on demand. Walking from a paradigm resolves its referenced directives on demand, fetches only the
 * reachable sub-graph (strictly fewer than the pack's directive count), and continues past a target of an unwired kind
 * (a typed {@code procedure} reference), which resolves empty.
 */
class ParadigmWalkSliceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    // clean-architecture -> directive:BETA (a requires edge, reachable) and -> procedure:some-procedure (a suggests
    // edge to an unwired kind, D6).
    private final Paradigm start = Paradigm.builder()
            .id(new ParadigmIdentifier("clean-architecture"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Clean Architecture")
            .summary("Dependencies point inward")
            .reference(new ParadigmReference("procedure", "some-procedure"))
            .directiveRef("BETA")
            .build();

    private final Directive beta = leaf("BETA");
    private final Directive gamma = leaf("GAMMA"); // unreachable from the paradigm
    private final Directive delta = leaf("DELTA"); // unreachable from the paradigm

    private final Map<DirectiveIdentifier, Directive> directives =
            Map.of(beta.id(), beta, gamma.id(), gamma, delta.id(), delta);
    private final Map<ParadigmIdentifier, Paradigm> paradigms = Map.of(start.id(), start);

    private final DirectiveReadService directiveReads = new DirectiveReadService(
            new FixedPacks(), new FixedDirectives(), new ConsistentRead(() -> "sha256:" + "a".repeat(64)));
    private final ParadigmReadService paradigmReads = new ParadigmReadService(
            new FixedPacks(), new FixedParadigms(), new ConsistentRead(() -> "sha256:" + "a".repeat(64)));

    @Test
    void walksFromAParadigmResolvingItsReferencedDirectivesOnDemandAndContinuesPastAnUnwiredKind() {
        AtomicInteger resolverCalls = new AtomicInteger();
        Set<Urn> fetched = new HashSet<>();
        ComponentResolver resolver = urn -> {
            resolverCalls.incrementAndGet();
            try {
                if (urn.kind() == NodeKind.DIRECTIVE) {
                    Directive found = directiveReads
                            .directive(PACK, new DirectiveIdentifier(urn.id()))
                            .value();
                    fetched.add(urn);
                    return Optional.of((CharterArtifact) found);
                }
                if (urn.kind() == NodeKind.PARADIGM) {
                    Paradigm found = paradigmReads
                            .paradigm(PACK, new ParadigmIdentifier(urn.id()))
                            .value();
                    fetched.add(urn);
                    return Optional.of((CharterArtifact) found);
                }
                return Optional.empty(); // D6: an unwired/out-of-scope kind resolves empty, the walk continues.
            } catch (CharterRefusal notFound) {
                return Optional.empty();
            }
        };

        List<Urn> visited = new ArrayList<>();
        new GraphWalk(resolver).walk(start, new GraphWalker() {
            @Override
            public Descent onNode(DrgNode node) {
                visited.add(node.urn());
                return Descent.CONTINUE;
            }

            @Override
            public Descent onRelationship(Relationship relationship) {
                return Descent.CONTINUE;
            }
        });

        // The start plus only the reachable directive; the unreachable ones are never visited.
        assertThat(visited)
                .contains(start.urn(), new Urn(NodeKind.DIRECTIVE, "BETA"))
                .doesNotContain(new Urn(NodeKind.DIRECTIVE, "GAMMA"), new Urn(NodeKind.DIRECTIVE, "DELTA"));
        // SC-002: the walk fetched strictly fewer components than the pack's total directive count.
        assertThat(fetched).containsExactly(new Urn(NodeKind.DIRECTIVE, "BETA"));
        assertThat(fetched.size()).isLessThan(directives.size());
        // D6: the procedure target was attempted (one resolver call) and resolved empty, yet BETA was visited.
        assertThat(resolverCalls.get()).isEqualTo(2);
    }

    private Directive leaf(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(id)
                .intent("leaf")
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private final class FixedDirectives implements DirectiveRepository {
        @Override
        public void save(Directive directive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier directive) {
            return Optional.ofNullable(directives.get(directive));
        }
    }

    private final class FixedParadigms implements ParadigmRepository {
        @Override
        public void save(Paradigm paradigm) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier paradigm) {
            return Optional.ofNullable(paradigms.get(paradigm));
        }
    }

    private final class FixedPacks implements PackRepository {
        @Override
        public Pack load(PackIdentifier pack) {
            Pack.Builder builder = Pack.builder()
                    .id(PACK)
                    .descriptor(new PackDescriptor(
                            PACK.value(),
                            "1.0.0",
                            Optional.empty(),
                            PACK.value(),
                            Optional.empty(),
                            Optional.empty(),
                            List.of()));
            directives.values().forEach(builder::directive);
            paradigms.values().forEach(builder::paradigm);
            return builder.build();
        }
    }
}
