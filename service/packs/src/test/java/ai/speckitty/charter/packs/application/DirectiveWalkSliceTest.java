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
import ai.speckitty.charter.packs.domain.DirectiveReference;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The US1 traversal half (SC-002, research D6): a {@link ComponentResolver} backed by {@link DirectiveReadService}
 * drives a {@link GraphWalk} on demand. The walk fetches only the reachable sub-graph (strictly fewer than the pack's
 * directive count) and continues past a target of an unwired kind, which resolves empty.
 */
class DirectiveWalkSliceTest {

    /** A walk resolves items without their revision, which reads every pack file; it must never ask for one. */
    private static final PackRevisions NO_REVISION = pack -> {
        throw new AssertionError("a walk never reads the pack revision");
    };

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    // ALPHA -> directive:BETA (reachable) and -> procedure:some-procedure (unwired kind, D6).
    private final Directive alpha = Directive.builder()
            .id(new DirectiveIdentifier("ALPHA"))
            .packId(PACK)
            .schemaVersion("1.0")
            .title("Alpha")
            .intent("start")
            .enforcement(Enforcement.REQUIRED)
            .procedure("some-procedure")
            .reference(new DirectiveReference("directive", "BETA", Optional.empty(), Optional.empty()))
            .build();
    private final Directive beta = leaf("BETA");
    private final Directive gamma = leaf("GAMMA"); // unreachable from ALPHA
    private final Directive delta = leaf("DELTA"); // unreachable from ALPHA

    private final Map<DirectiveIdentifier, Directive> directives =
            Map.of(alpha.id(), alpha, beta.id(), beta, gamma.id(), gamma, delta.id(), delta);

    private final DirectiveReadService reads = new DirectiveReadService(
            new FixedPacks(), new FixedDirectives(), new ConsistentRead(() -> "sha256:" + "a".repeat(64)), NO_REVISION);

    @Test
    void walksOnlyTheReachableSubgraphAndContinuesPastAnUnwiredKind() {
        AtomicInteger resolverCalls = new AtomicInteger();
        Set<Urn> fetched = new HashSet<>();
        ComponentResolver resolver = urn -> {
            resolverCalls.incrementAndGet();
            if (urn.kind() != NodeKind.DIRECTIVE) {
                return Optional.empty(); // D6: an unwired/out-of-scope kind resolves empty, the walk continues.
            }
            try {
                Directive found =
                        reads.directive(PACK, new DirectiveIdentifier(urn.id())).value();
                fetched.add(urn);
                return Optional.of((CharterArtifact) found);
            } catch (CharterRefusal notFound) {
                return Optional.empty();
            }
        };

        List<Urn> visited = new ArrayList<>();
        new GraphWalk(resolver).walk(alpha, new GraphWalker() {
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
                .contains(alpha.urn(), new Urn(NodeKind.DIRECTIVE, "BETA"))
                .doesNotContain(new Urn(NodeKind.DIRECTIVE, "GAMMA"), new Urn(NodeKind.DIRECTIVE, "DELTA"));
        // SC-002: the walk fetched strictly fewer components than the pack's total directive count.
        assertThat(fetched).containsExactly(new Urn(NodeKind.DIRECTIVE, "BETA"));
        assertThat(fetched.size()).isLessThan(directives.size());
        // D6: the procedure target was attempted (one resolver call for it) and resolved empty, yet BETA was visited.
        assertThat(resolverCalls.get()).isEqualTo(2);
        assertThat(visited).contains(new Urn(NodeKind.DIRECTIVE, "BETA"));
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
            return builder.build();
        }
    }
}
