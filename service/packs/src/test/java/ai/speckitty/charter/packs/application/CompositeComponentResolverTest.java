package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.TacticStep;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The production {@link ComponentResolver} (ADR-0025 Decision 4): a {@link CompositeComponentResolver} bound to a pack
 * dispatches a neighbour URN on its kind to that kind's read path and fetches only that one component. A target of an
 * unwired, out-of-scope or external kind resolves empty (research Decision 6) so a walk continues past it, and an absent
 * in-scope component resolves empty too; the resolver never throws for an unresolvable kind.
 */
class CompositeComponentResolverTest {

    /** A walk resolves items without their revision, which reads every pack file; it must never ask for one. */
    private static final PackRevisions NO_REVISION = pack -> {
        throw new AssertionError("a walk never reads the pack revision");
    };

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private final Directive directive = Directive.builder()
            .id(new DirectiveIdentifier("DIRECTIVE_1"))
            .packId(PACK)
            .schemaVersion("1.0")
            .title("Directive one")
            .intent("intent")
            .enforcement(Enforcement.REQUIRED)
            .build();
    private final Styleguide styleguide = Styleguide.builder()
            .id(new StyleguideIdentifier("style-1"))
            .packId(PACK)
            .schemaVersion("1.0")
            .title("Style one")
            .scope(StyleguideScope.CODE)
            .principle("Prefer immutability")
            .build();
    private final Paradigm paradigm = Paradigm.builder()
            .id(new ParadigmIdentifier("paradigm-1"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Paradigm one")
            .summary("summary")
            .build();
    private final Toolguide toolguide = Toolguide.builder()
            .id(new ToolguideIdentifier("toolguide-1"))
            .packId(PACK)
            .schemaVersion("1.0")
            .tool("t")
            .title("Toolguide one")
            .guide(Guide.of("How to use the tool."))
            .summary("summary")
            .build();
    private final Tactic tactic = Tactic.builder()
            .id(new TacticIdentifier("tactic-1"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Tactic one")
            .purpose("purpose")
            .step(new TacticStep("Only step", Optional.empty(), java.util.List.of(), java.util.List.of()))
            .build();

    private final ComponentResolver resolver = new CompositeComponentResolver(
                    new DirectiveReadService(new UnusedPacks(), new FixedDirectives(), fixedDigest(), NO_REVISION),
                    new StyleguideReadService(new UnusedPacks(), new FixedStyleguides(), fixedDigest(), NO_REVISION),
                    new ParadigmReadService(new UnusedPacks(), new FixedParadigms(), fixedDigest(), NO_REVISION),
                    new ToolguideReadService(new UnusedPacks(), new FixedToolguides(), fixedDigest(), NO_REVISION),
                    new TacticReadService(new UnusedPacks(), new FixedTactics(), fixedDigest()))
            .forPack(PACK);

    @Test
    void resolvesEachInScopeKindThroughItsMatchingReadPath() {
        assertResolvesTo(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_1"), ArtifactKind.DIRECTIVE, "DIRECTIVE_1");
        assertResolvesTo(new Urn(NodeKind.STYLEGUIDE, "style-1"), ArtifactKind.STYLEGUIDE, "style-1");
        assertResolvesTo(new Urn(NodeKind.PARADIGM, "paradigm-1"), ArtifactKind.PARADIGM, "paradigm-1");
        assertResolvesTo(new Urn(NodeKind.TOOLGUIDE, "toolguide-1"), ArtifactKind.TOOLGUIDE, "toolguide-1");
        assertResolvesTo(new Urn(NodeKind.TACTIC, "tactic-1"), ArtifactKind.TACTIC, "tactic-1");
    }

    @Test
    void returnsEmptyForAnUnwiredOutOfScopeOrExternalKind() {
        // Research Decision 6: a target of a kind no read path serves resolves empty and the walk continues past it.
        assertThat(resolver.resolve(new Urn(NodeKind.PROCEDURE, "some-procedure")))
                .isEmpty();
        assertThat(resolver.resolve(new Urn(NodeKind.ASSET, "some-asset"))).isEmpty();
        assertThat(resolver.resolve(new Urn(NodeKind.AGENT_PROFILE, "some-agent")))
                .isEmpty();
    }

    @Test
    void returnsEmptyForAnAbsentInScopeComponentAndNeverThrows() {
        // An in-scope kind whose component the pack does not hold is the same empty answer as an unwired kind: the
        // read path refuses *_NOT_FOUND and the resolver maps it to empty, never rethrowing it as a walk failure.
        assertThatCode(() -> assertThat(resolver.resolve(new Urn(NodeKind.DIRECTIVE, "MISSING")))
                        .isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void neverThrowsForAKindNoReadPathEvenConsiders() {
        for (NodeKind kind :
                new NodeKind[] {NodeKind.ACTION, NodeKind.MISSION_TYPE, NodeKind.TEMPLATE, NodeKind.SKILL}) {
            assertThatCode(() ->
                            assertThat(resolver.resolve(new Urn(kind, "x"))).isEmpty())
                    .as("resolving %s never throws", kind)
                    .doesNotThrowAnyException();
        }
    }

    private void assertResolvesTo(Urn urn, ArtifactKind kind, String localId) {
        Optional<CharterArtifact> resolved = resolver.resolve(urn);
        assertThat(resolved).as("resolving %s", urn).isPresent();
        assertThat(resolved.get().kind()).isEqualTo(kind);
        assertThat(resolved.get().localId()).isEqualTo(localId);
        assertThat(resolved.get().packId()).isEqualTo(PACK);
    }

    private static ConsistentRead fixedDigest() {
        return new ConsistentRead(() -> "sha256:" + "a".repeat(64));
    }

    /** A pack port the single-component read path never consults; the list path is not exercised here. */
    private static final class UnusedPacks implements PackRepository {
        @Override
        public Pack load(PackIdentifier pack) {
            throw new UnsupportedOperationException("the resolver reads single components, never the whole pack");
        }
    }

    private final class FixedDirectives implements DirectiveRepository {
        @Override
        public void save(Directive value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier id) {
            return Optional.of(directive).filter(d -> d.id().equals(id));
        }
    }

    private final class FixedStyleguides implements StyleguideRepository {
        @Override
        public void save(Styleguide value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Styleguide> load(PackIdentifier pack, StyleguideIdentifier id) {
            return Optional.of(styleguide).filter(s -> s.id().equals(id));
        }
    }

    private final class FixedParadigms implements ParadigmRepository {
        @Override
        public void save(Paradigm value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier id) {
            return Optional.of(paradigm).filter(p -> p.id().equals(id));
        }
    }

    private final class FixedToolguides implements ToolguideRepository {
        @Override
        public void save(Toolguide value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Toolguide> load(PackIdentifier pack, ToolguideIdentifier id) {
            return Optional.of(toolguide).filter(t -> t.id().equals(id));
        }
    }

    private final class FixedTactics implements TacticRepository {
        @Override
        public void save(Tactic value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Tactic> load(PackIdentifier pack, TacticIdentifier id) {
            return Optional.of(tactic).filter(t -> t.id().equals(id));
        }
    }
}
