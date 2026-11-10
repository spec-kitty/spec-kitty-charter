package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The production {@link ComponentResolver} (ADR-0025 Decision 4): a {@link CompositeComponentResolver} bound to a pack
 * dispatches a neighbour URN on its kind to that kind's read path and fetches only that one component. A target of an
 * unwired, out-of-scope or external kind resolves empty (research Decision 6) so a walk continues past it, and an absent
 * in-scope component resolves empty too; the resolver never throws for an unresolvable kind.
 *
 * <p>The pack-qualified reference grammar (FR-013, FR-014, FR-015, FR-017) resolves through the same resolver against
 * the compose closure the offering already resolved: a qualified reference is held by the pack it names, a bare reference
 * is held only by the referring pack, and the two refusals name their packs.
 */
class CompositeComponentResolverTest {

    /** A walk resolves items without their revision, which reads every pack file; it must never ask for one. */
    private static final PackRevisions NO_REVISION = pack -> {
        throw new AssertionError("a walk never reads the pack revision");
    };

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    /** The second pack of the shared two-pack fixture (SC-004): bravo, holding directive SHARED_1. */
    private static final PackIdentifier PACK_B = new PackIdentifier("pack-b");

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
            .principle("Prefer immutability", "Prefer immutability, in one sentence.")
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
            .step(new TacticStep("Only step", Optional.empty(), java.util.List.of()))
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

    // ---- the pack-qualified reference grammar (FR-013, FR-014, FR-015, FR-017; SC-004's shared fixture) --------

    @Test
    void aPackQualifiedReferenceIsHeldByTheNamedPackOfTheClosure() {
        assertThat(holderOf(twoPackResolver(), "bravo::directive:SHARED_1")).isEqualTo(PACK_B);
    }

    @Test
    void aBareReferenceThatIsHeldOnlyByAnotherPackIsRefusedNamingTheHoldingPack() {
        assertThatThrownBy(() -> holderOf(twoPackResolver(), "directive:SHARED_1"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
                    assertThat(refused.getMessage()).contains("bravo").contains("pack-qualified");
                });
    }

    @Test
    void aBareReferenceIsHeldByTheReferringPackWhenAnotherPackHoldsTheSameIdAndTheQualifiedOneByTheOther() {
        // FR-014: alpha writes directive:SHARE bare, and bravo holds one too. Alpha's own shadows it, with no refusal.
        CompositeComponentResolver.Composed shadowing = shadowingResolver();

        assertThat(holderOf(shadowing, "directive:SHARE")).isEqualTo(PACK);
        assertThat(holderOf(shadowing, "bravo::directive:SHARE")).isEqualTo(PACK_B);
    }

    @Test
    void aBareReferenceHeldByNoPackHasNoHolder() {
        assertThat(twoPackResolver().holder(PackQualifiedReference.parse("directive:NOWHERE"), PACK))
                .isEmpty();
    }

    /** The pack of the compose that holds what the reference names, from alpha's perspective. */
    private static PackIdentifier holderOf(CompositeComponentResolver.Composed composed, String reference) {
        return composed.holder(PackQualifiedReference.parse(reference), PACK)
                .orElseThrow()
                .configured()
                .id();
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

    /**
     * The shared two-pack fixture of the grammar (SC-004): alpha ({@code pack-a}, directive {@code OWN_1}) and bravo
     * ({@code pack-b}, directive {@code SHARED_1}) in one compose closure, resolved through the offering.
     */
    private CompositeComponentResolver.Composed twoPackResolver() {
        DirectivesByPack directives = new DirectivesByPack();
        directives.held.put(PACK, directiveIn(PACK, "OWN_1"));
        directives.held.put(PACK_B, directiveIn(PACK_B, "SHARED_1"));
        return composed(
                directives,
                Map.of(
                        PACK, List.of("directive:OWN_1"),
                        PACK_B, List.of("directive:SHARED_1", "procedure:release")),
                PackLevel.ORG);
    }

    /**
     * The FR-014 fixture: alpha (project) and bravo (org) both hold {@code directive:SHARE}, so alpha's shadows
     * bravo's, as a project artifact shadows an org one.
     */
    private CompositeComponentResolver.Composed shadowingResolver() {
        DirectivesByPack directives = new DirectivesByPack();
        directives.held.put(PACK, directiveIn(PACK, "SHARE"));
        directives.held.put(PACK_B, directiveIn(PACK_B, "SHARE"));
        return composed(
                directives,
                Map.of(PACK, List.of("directive:SHARE"), PACK_B, List.of("directive:SHARE")),
                PackLevel.PROJECT);
    }

    /** The compose over alpha at {@code alphaLevel} and bravo at org, resolved once, as a read resolves it. */
    private CompositeComponentResolver.Composed composed(
            DirectivesByPack directives, Map<PackIdentifier, List<String>> held, PackLevel alphaLevel) {
        return new CompositeComponentResolver(
                        new DirectiveReadService(new UnusedPacks(), directives, fixedDigest(), NO_REVISION),
                        new StyleguideReadService(
                                new UnusedPacks(), new FixedStyleguides(), fixedDigest(), NO_REVISION),
                        new ParadigmReadService(new UnusedPacks(), new FixedParadigms(), fixedDigest(), NO_REVISION),
                        new ToolguideReadService(new UnusedPacks(), new FixedToolguides(), fixedDigest(), NO_REVISION),
                        new TacticReadService(new UnusedPacks(), new FixedTactics(), fixedDigest()))
                .over(new OfferingReadService(
                                () -> List.of(
                                        new ConfiguredPack(PACK, alphaLevel),
                                        new ConfiguredPack(PACK_B, PackLevel.ORG)),
                                new NamedDescriptors(),
                                new PackArtifactRepository() {
                                    @Override
                                    public PackCompositionFacts compositionFacts(PackIdentifier pack) {
                                        return new PackCompositionFacts(List.of(), held.get(pack), List.of());
                                    }
                                },
                                fixedDigest())
                        .composed());
    }

    private static Directive directiveIn(PackIdentifier pack, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .intent("intent")
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    /** The directives each pack of the two-pack fixture holds, by pack. */
    private static final class DirectivesByPack implements DirectiveRepository {
        private final Map<PackIdentifier, Directive> held = new LinkedHashMap<>();

        @Override
        public void save(Directive value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier id) {
            return Optional.ofNullable(held.get(pack))
                    .filter(directive -> directive.id().equals(id));
        }
    }

    /** The descriptors of the two-pack compose: {@code pack-a} is alpha, {@code pack-b} is bravo. */
    private static final class NamedDescriptors implements PackDescriptorRepository {
        @Override
        public PackDescriptor get(PackIdentifier pack) {
            return find(pack).orElseThrow();
        }

        @Override
        public Optional<PackDescriptor> find(PackIdentifier pack) {
            return Optional.of(new PackDescriptor(
                    pack.value(),
                    "1.0.0",
                    Optional.empty(),
                    pack.equals(PACK_B) ? "bravo" : "alpha",
                    Optional.of(PackLevel.ORG),
                    Optional.empty(),
                    PackPaths.none(),
                    // alpha relies on bravo, so bravo is in alpha's own closure
                    pack.equals(PACK_B) ? List.of() : List.of(new PackDependency(PACK_B.value(), ">=1.0"))));
        }

        @Override
        public void save(PackIdentifier pack, PackDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }
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
