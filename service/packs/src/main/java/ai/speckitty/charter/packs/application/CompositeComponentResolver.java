package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.ComposeResolver;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The production {@link ComponentResolver} (ADR-0025 Decision 4): it dispatches a neighbour URN on its
 * {@link Urn#kind() kind} to that kind's read service and fetches only that one component, so a {@link GraphWalk} is
 * bounded by the walk rather than by pack size (NFR-001). It composes the per-kind read services only and never reaches
 * into storage; the dispatch is a plain {@code switch} on the node kind, not a shared registry (research Decision 5).
 *
 * <p>For a target whose kind is out of scope, unwired or external — an asset, an agent profile, and the like, and a
 * procedure, which the artifact reads serve (#484) but no walk resolves yet (#488) — it returns {@link Optional#empty()} (research Decision 6), so a walk simply continues past it; the same empty
 * answers an in-scope kind whose component is absent (the read service refuses {@code *_NOT_FOUND}). It
 * <strong>never</strong> throws for an unresolvable kind: emptiness is the normal answer for the 44% of real edges that
 * target currently-unwired kinds.
 *
 * <p>A {@link Urn} is bare, naming only a {@code kind} and an {@code id}, so a pack is bound once —
 * {@link #forPack(PackIdentifier)} yields the pack-scoped {@link ComponentResolver} the application-layer walk driver
 * consumes. This service is component-scanned; the composition root needs no {@code @Bean}.
 *
 * <p>Besides the in-pack URNs, it names the pack that holds a reference as an artifact writes it — bare or
 * pack-qualified — against a compose closure the caller resolved once for its read ({@link #over(List)}). The two
 * graphs stay unmerged (research): the pack composition is handed in read-only, and the component resolution still
 * goes through the per-kind read services alone.
 */
@Service
public final class CompositeComponentResolver {

    private final DirectiveReadService directives;
    private final StyleguideReadService styleguides;
    private final ParadigmReadService paradigms;
    private final ToolguideReadService toolguides;
    private final TacticReadService tactics;

    /**
     * A resolver over the injected per-kind read services.
     *
     * @param directives the directive read path
     * @param styleguides the styleguide read path
     * @param paradigms the paradigm read path
     * @param toolguides the toolguide read path
     * @param tactics the tactic read path
     */
    @Autowired
    public CompositeComponentResolver(
            DirectiveReadService directives,
            StyleguideReadService styleguides,
            ParadigmReadService paradigms,
            ToolguideReadService toolguides,
            TacticReadService tactics) {
        this.directives = Objects.requireNonNull(directives, "directives");
        this.styleguides = Objects.requireNonNull(styleguides, "styleguides");
        this.paradigms = Objects.requireNonNull(paradigms, "paradigms");
        this.toolguides = Objects.requireNonNull(toolguides, "toolguides");
        this.tactics = Objects.requireNonNull(tactics, "tactics");
    }

    /**
     * The {@link ComponentResolver} bound to one pack: every URN it resolves is read from {@code pack}'s read paths.
     * A URN is bare, so the resolver knows nothing of a pack qualifier; a walk across packs asks the
     * {@linkplain #over(List) compose} which pack a qualified reference names, then binds the resolver to that pack.
     *
     * @param pack the pack whose DRG is being walked
     * @return a resolver that dispatches a neighbour URN to its kind's read path within {@code pack}
     */
    public ComponentResolver forPack(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return urn -> resolve(pack, urn);
    }

    /**
     * The packs of one resolved compose, as a {@link ComposeResolver} a walk can cross packs with. The closure is the
     * one the caller resolved for the read it serves, so a read resolves the compose once, never once per hop.
     *
     * @param closure the compose's closure, as the offering resolved it
     * @return the compose view over those packs
     */
    public Composed over(List<PackCompositionResolver.PackUnderResolution> closure) {
        return new Composed(List.copyOf(Objects.requireNonNull(closure, "closure")));
    }

    /** The compose a read resolved once: the pack a name stands for, a resolver per pack, and the holder of a reference. */
    public final class Composed implements ComposeResolver {

        private final List<PackCompositionResolver.PackUnderResolution> closure;

        private Composed(List<PackCompositionResolver.PackUnderResolution> closure) {
            this.closure = closure;
        }

        @Override
        public Optional<PackIdentifier> holdingPack(PackQualifiedReference reference, PackIdentifier referringPack) {
            return holder(reference, referringPack)
                    .map(PackCompositionResolver.PackUnderResolution::configured)
                    .map(ConfiguredPack::id);
        }

        @Override
        public ComponentResolver inPack(PackIdentifier pack) {
            return forPack(pack);
        }

        /**
         * The pack of the compose that holds what a reference names, as an artifact writes it (FR-013, FR-014, FR-015,
         * FR-017), by the compose's one rule ({@link PackCompositionResolver#holderOf}) and judged by its one check
         * ({@link PackCompositionResolver#checkReferences}); the walk seam asks it too. A qualified reference is held by the pack
         * it names; a name the compose does not hold is refused {@link RefusalCode#PACK_REFERENCE_PACK_MISSING}, naming
         * the missing pack, and never falls back to the referring pack. A bare one is held by the referring pack when
         * it holds the artifact, which shadows a same-named artifact of another pack (FR-014); when only another pack
         * holds it the reference is refused {@link RefusalCode#PACK_REFERENCE_UNQUALIFIED}, naming the holding pack, so
         * it cannot resolve by load order. When no pack holds it nothing holds it.
         *
         * <p>Both verdicts are the compose resolver's, so the reference codes travel the same path the other compose
         * refusals travel. The offering already checked every reference its packs write when it resolved the closure,
         * so here the check is a defensive assertion for a closure that did not come from it.
         *
         * @param parsed the reference as the artifact writes it
         * @param referringPack the pack whose artifact writes it
         * @return the holding pack; empty when no pack of the compose holds what a bare reference names
         * @throws CharterRefusal with {@link RefusalCode#PACK_REFERENCE_PACK_MISSING} when a qualified reference names
         *     a pack outside the referring pack's {@code depends_on} closure, or with {@link RefusalCode#PACK_REFERENCE_UNQUALIFIED} when a bare reference
         *     resolves only in another pack of the compose
         */
        public Optional<PackCompositionResolver.PackUnderResolution> holder(
                PackQualifiedReference parsed, PackIdentifier referringPack) {
            return holderIn(closure, parsed, referringPack);
        }
    }

    /**
     * The holder of a reference in one resolved compose, judged by the compose's one rule and one check: what
     * {@link Composed#holder} answers, for a caller that holds the closure and no resolver.
     *
     * @param closure the compose's closure, as the offering resolved it
     * @param parsed the reference as the artifact writes it
     * @param referringPack the pack whose artifact writes it
     * @return the holding pack; empty when no pack of the compose holds what a bare reference names
     * @throws CharterRefusal as {@link Composed#holder} does
     */
    static Optional<PackCompositionResolver.PackUnderResolution> holderIn(
            List<PackCompositionResolver.PackUnderResolution> closure,
            PackQualifiedReference parsed,
            PackIdentifier referringPack) {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(referringPack, "referringPack");
        Optional<PackCompositionResolver.PackUnderResolution> holder =
                PackCompositionResolver.holderOf(closure, referringPack, parsed);
        String referringId = closure.stream()
                .filter(pack -> pack.configured().id().equals(referringPack))
                .map(pack -> pack.descriptor().packId())
                .findFirst()
                .orElse(referringPack.value());
        checked(
                closure,
                referringId,
                new PackCompositionResolver.ResolvedReference(
                        parsed, holder.map(pack -> pack.descriptor().packId())));
        return holder;
    }

    /** The closure check of the compose resolver, its refusal mapped to the reference code the contract serves. */
    private static void checked(
            List<PackCompositionResolver.PackUnderResolution> closure,
            String referringPackId,
            PackCompositionResolver.ResolvedReference resolved) {
        try {
            PackCompositionResolver.checkReferences(closure, referringPackId, List.of(resolved));
        } catch (PackCompositionResolver.Refusal refusal) {
            throw refusal.toCharterRefusal();
        }
    }

    private Optional<CharterArtifact> resolve(PackIdentifier pack, Urn urn) {
        return switch (urn.kind()) {
            case DIRECTIVE ->
                read(() -> directives
                        .directive(pack, new DirectiveIdentifier(urn.id()))
                        .value());
            case STYLEGUIDE ->
                read(() -> styleguides
                        .styleguide(pack, new StyleguideIdentifier(urn.id()))
                        .value());
            case PARADIGM ->
                read(() -> paradigms
                        .paradigm(pack, new ParadigmIdentifier(urn.id()))
                        .value());
            case TOOLGUIDE ->
                read(() -> toolguides
                        .toolguide(pack, new ToolguideIdentifier(urn.id()))
                        .value());
            case TACTIC ->
                read(() -> tactics.tactic(pack, new TacticIdentifier(urn.id())).value());
            // D6: a procedure (served, not walked yet: #488), asset, agent_profile, action, mission_type and every
            // other unwired/out-of-scope kind
            // resolves empty, and the walk continues past it.
            default -> Optional.empty();
        };
    }

    /**
     * Reads one component, mapping a {@code *_NOT_FOUND} refusal to {@link Optional#empty()} so a walk continues past an
     * absent in-scope component just as it does past an unwired kind (research Decision 6). A refusal of any other code
     * (an unreadable pack, a stale read) stays a refusal — it is not an empty neighbour.
     */
    private static Optional<CharterArtifact> read(Supplier<? extends CharterArtifact> load) {
        try {
            return Optional.of(load.get());
        } catch (CharterRefusal refused) {
            if (refused.code().notFound()) {
                return Optional.empty();
            }
            throw refused;
        }
    }
}
