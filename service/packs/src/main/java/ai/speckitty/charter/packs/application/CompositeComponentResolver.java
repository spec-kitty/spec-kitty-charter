package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.ComposeResolver;
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
 * <p>For a target whose kind is out of scope, unwired or external — a procedure, an asset, an agent profile, and the
 * like — it returns {@link Optional#empty()} (research Decision 6), so a walk simply continues past it; the same empty
 * answers an in-scope kind whose component is absent (the read service refuses {@code *_NOT_FOUND}). It
 * <strong>never</strong> throws for an unresolvable kind: emptiness is the normal answer for the 44% of real edges that
 * target currently-unwired kinds.
 *
 * <p>A {@link Urn} names only a {@code kind} and an {@code id}; a pack's DRG is walked within one pack, so the pack is
 * bound once — {@link #forPack(PackIdentifier)} yields the pack-scoped {@link ComponentResolver} the application-layer
 * walk driver consumes, exactly as the slice tests bind their resolver to the pack under walk. This service is
 * component-scanned; the composition root needs no {@code @Bean}.
 *
 * <p>Besides the in-pack URNs, it resolves a reference as an artifact writes it — bare or pack-qualified — against the
 * compose closure the offering already resolved ({@link #resolveReference(String, PackIdentifier)}). The two graphs
 * stay unmerged (research): the pack composition is consumed read-only through {@link OfferingReadService}, and the
 * component resolution still goes through the per-kind read services alone.
 */
@Service
public final class CompositeComponentResolver {

    private final DirectiveReadService directives;
    private final StyleguideReadService styleguides;
    private final ParadigmReadService paradigms;
    private final ToolguideReadService toolguides;
    private final TacticReadService tactics;
    private final OfferingReadService offerings;

    /**
     * A resolver over the injected per-kind read services and the offering that owns the compose closure.
     *
     * @param directives the directive read path
     * @param styleguides the styleguide read path
     * @param paradigms the paradigm read path
     * @param toolguides the toolguide read path
     * @param tactics the tactic read path
     * @param offerings the offering read whose resolved closure a pack-qualified reference is resolved against
     */
    @Autowired
    public CompositeComponentResolver(
            DirectiveReadService directives,
            StyleguideReadService styleguides,
            ParadigmReadService paradigms,
            ToolguideReadService toolguides,
            TacticReadService tactics,
            OfferingReadService offerings) {
        this.directives = Objects.requireNonNull(directives, "directives");
        this.styleguides = Objects.requireNonNull(styleguides, "styleguides");
        this.paradigms = Objects.requireNonNull(paradigms, "paradigms");
        this.toolguides = Objects.requireNonNull(toolguides, "toolguides");
        this.tactics = Objects.requireNonNull(tactics, "tactics");
        this.offerings = Objects.requireNonNull(offerings, "offerings");
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

    /** The compose a read resolved once: the pack a name stands for, a resolver per pack, and references. */
    public final class Composed implements ComposeResolver {

        private final List<PackCompositionResolver.PackUnderResolution> closure;

        private Composed(List<PackCompositionResolver.PackUnderResolution> closure) {
            this.closure = closure;
        }

        @Override
        public Optional<PackIdentifier> packNamed(String name) {
            return closure.stream()
                    .filter(pack -> pack.descriptor().name().equals(name))
                    .map(pack -> pack.configured().id())
                    .findFirst();
        }

        @Override
        public ComponentResolver inPack(PackIdentifier pack) {
            return forPack(pack);
        }

        /**
         * Resolves one reference as an artifact writes it — bare ({@code <urn>}), a reference inside the referring
         * pack, or pack-qualified ({@code <pack-name>::<urn>}), which names the pack it relies on (FR-013, FR-014,
         * FR-015, FR-017). The reference resolves to {@code (holding pack, bare URN)}: a qualified reference names a
         * pack of the compose, and its URN part resolves inside that pack through the same per-kind dispatch an in-pack
         * neighbour takes — the referring pack's own artifacts are reachable both bare and qualified. A name the
         * compose does not hold is refused {@link RefusalCode#PACK_REFERENCE_PACK_MISSING}, naming the missing pack,
         * and never falls back to the referring pack. A bare reference resolves only inside the referring pack; when
         * its id resolves only in another pack of the compose, that accidental coupling is refused
         * {@link RefusalCode#PACK_REFERENCE_UNQUALIFIED}, naming the holding pack, so it cannot resolve by load order.
         * A bare id that resolves nowhere keeps the empty answer an absent in-scope component always gets.
         *
         * <p>Both verdicts are the compose resolver's: {@link PackCompositionResolver#checkReferences} refuses, so the
         * reference codes travel the same path the other compose refusals travel. A token no known kind holds resolves
         * empty, exactly as a bare neighbour of an unwired kind does (research Decision 6).
         *
         * @param reference the reference as the artifact writes it, for example
         *     {@code foundation::directive:DIRECTIVE_001}
         * @param referringPack the pack whose artifact writes the reference; a bare reference resolves only inside it
         * @return the referenced component when its kind is in scope and the pack it names holds it; empty when it
         *     resolves nowhere in the compose
         * @throws IllegalArgumentException when the reference is outside the grammar (a malformed qualified form)
         * @throws CharterRefusal with {@link RefusalCode#PACK_REFERENCE_PACK_MISSING} when a qualified reference names
         *     a pack outside the compose, or with {@link RefusalCode#PACK_REFERENCE_UNQUALIFIED} when a bare reference
         *     resolves only in another pack of the compose
         */
        public Optional<CharterArtifact> resolveReference(String reference, PackIdentifier referringPack) {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(referringPack, "referringPack");
            PackQualifiedReference parsed = PackQualifiedReference.parse(reference);
            String referringId = closure.stream()
                    .filter(pack -> pack.configured().id().equals(referringPack))
                    .map(pack -> pack.descriptor().packId())
                    .findFirst()
                    .orElse(referringPack.value());
            if (parsed.qualified()) {
                checked(closure, referringId, new PackCompositionResolver.ResolvedReference(parsed, referringId));
                PackIdentifier holder =
                        packNamed(parsed.packName().orElseThrow()).orElseThrow();
                return resolve(holder, parsed.urn());
            }
            Optional<CharterArtifact> inPack = resolve(referringPack, parsed.urn());
            if (inPack.isPresent()) {
                return inPack;
            }
            for (PackCompositionResolver.PackUnderResolution pack : closure) {
                if (pack.configured().id().equals(referringPack)) {
                    continue;
                }
                if (pack.holds(parsed.urn())) {
                    checked(
                            closure,
                            referringId,
                            new PackCompositionResolver.ResolvedReference(
                                    parsed, pack.descriptor().packId()));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Resolves one reference against the compose the offering resolves now.
     *
     * @param reference the reference as the artifact writes it
     * @param referringPack the pack whose artifact writes the reference
     * @return the referenced component; empty when it resolves nowhere in the compose
     * @see Composed#resolveReference(String, PackIdentifier)
     */
    public Optional<CharterArtifact> resolveReference(String reference, PackIdentifier referringPack) {
        return over(offerings.compose().value()).resolveReference(reference, referringPack);
    }

    /**
     * Resolves the URN part of a reference inside one pack. The URN is opaque to the reference grammar (C-005): it
     * reaches the existing {@link Urn} machinery verbatim, and a token no known kind holds resolves empty, exactly as
     * a bare neighbour of an unwired kind does (research Decision 6).
     */
    private Optional<CharterArtifact> resolve(PackIdentifier pack, String urn) {
        return Urn.parse(urn).flatMap(target -> resolve(pack, target));
    }

    /** The closure check of the compose resolver, its refusal mapped to the reference code the contract serves. */
    private static void checked(
            List<PackCompositionResolver.PackUnderResolution> closure,
            String referringPackId,
            PackCompositionResolver.ResolvedReference resolved) {
        try {
            PackCompositionResolver.checkReferences(closure, referringPackId, List.of(resolved));
        } catch (PackCompositionResolver.Refusal refusal) {
            throw new CharterRefusal(refusal.failure().code(), refusal.detail(), refusal.packs());
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
            // D6: a procedure, asset, agent_profile, action, mission_type and every other unwired/out-of-scope kind
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
