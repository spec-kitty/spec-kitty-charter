package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
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
     *
     * @param pack the pack whose DRG is being walked
     * @return a resolver that dispatches a neighbour URN to its kind's read path within {@code pack}
     */
    public ComponentResolver forPack(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return urn -> resolve(pack, urn);
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
