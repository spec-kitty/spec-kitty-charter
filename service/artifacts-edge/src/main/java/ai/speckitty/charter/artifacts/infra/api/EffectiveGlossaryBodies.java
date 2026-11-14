package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.EffectiveGlossaryContextRead;
import ai.speckitty.charter.api.model.EffectiveGlossaryTermRead;
import ai.speckitty.charter.api.model.GlossaryContextList;
import ai.speckitty.charter.api.model.GlossaryContextSummary;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.EffectiveGlossaryReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of the glossary reads under an activation (#106; FR-011, research R7): the facts the activation gives
 * the winning context and the context, its summary or one term as the pack-scoped reads map them
 * ({@link GlossaryBodies#context}, {@link GlossaryBodies#summary}, {@link GlossaryBodies#contextTerm}), so a context is
 * mapped once in the edge. The relation names are the skill twin's ({@link EffectiveBodies}). The links start at the
 * base path the controllers are mapped under.
 *
 * <ul>
 *   <li>The list links {@code self} and {@code up} (the activation) and names no pack; each item links
 *       {@code effective-artifact}, its read under the activation, and {@code artifact}, the pack-scoped read of the
 *       winning copy, as an item of the skill list does.
 *   <li>A context links {@code self}, {@code artifact}, {@code collection} (the activation's contexts), {@code term},
 *       templated by the term's id, and {@code up}.
 *   <li>A term links {@code self}, {@code artifact} (the term's pack-scoped read in the winner's pack) and {@code up}
 *       (its context under the activation). It carries its context's facts: a term has no state of its own.
 * </ul>
 */
@Component
public class EffectiveGlossaryBodies {

    private static final String ACTIVATIONS = "activations";
    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String TERMS = "terms";
    private static final String GLOSSARY = ArtifactKind.GLOSSARY.token();
    private static final String SELF = "self";
    private static final String UP = "up";
    private static final String ARTIFACT = "artifact";

    private final String basePath;
    private final GlossaryBodies glossaries;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     * @param glossaries the mapper of a context and its terms
     */
    @Autowired
    public EffectiveGlossaryBodies(
            @Value("${charter.api.base-path:/api/v1}") String basePath, GlossaryBodies glossaries) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.glossaries = Objects.requireNonNull(glossaries, "glossaries");
    }

    /**
     * The glossary contexts of an activation.
     *
     * @param listed what the application ring read
     * @return the {@code GlossaryContextList}, with no {@code packId}
     */
    public GlossaryContextList list(EffectiveGlossaryReads.Listed listed) {
        String activation = listed.scope().activationId();
        List<GlossaryContextSummary> items = listed.items().stream()
                .map(item -> {
                    String id = item.winner().id();
                    Map<String, Link> links = new LinkedHashMap<>();
                    links.put("effective-artifact", new Link(href(ACTIVATIONS, activation, ARTIFACTS, GLOSSARY, id)));
                    links.put(ARTIFACT, new Link(packContext(item.winner(), id)));
                    return GlossaryBodies.summary(item.context(), links);
                })
                .toList();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(href(ACTIVATIONS, activation, ARTIFACTS, GLOSSARY)));
        links.put(UP, new Link(href(ACTIVATIONS, activation)));
        return new GlossaryContextList(items, links);
    }

    /**
     * One glossary context under an activation.
     *
     * @param read what the application ring read
     * @return the {@code EffectiveGlossaryContextRead}
     */
    public EffectiveGlossaryContextRead context(EffectiveGlossaryReads.Read read) {
        ActivationArtifacts.Scope scope = read.scope();
        ActivationArtifacts.Facts facts = read.facts();
        ActivationArtifacts.Held winner = facts.held();
        String self = href(ACTIVATIONS, scope.activationId(), ARTIFACTS, GLOSSARY, winner.id());
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(self));
        links.put(ARTIFACT, new Link(packContext(winner, winner.id())));
        links.put("collection", new Link(href(ACTIVATIONS, scope.activationId(), ARTIFACTS, GLOSSARY)));
        links.put("term", new Link(self + "/" + TERMS + "/{termId}").templated(true));
        links.put(UP, new Link(href(ACTIVATIONS, scope.activationId())));
        EffectiveGlossaryContextRead wire = new EffectiveGlossaryContextRead(
                scope.activationId(),
                scope.revision(),
                scope.governanceFingerprint(),
                EffectiveGlossaryContextRead.KindEnum.GLOSSARY,
                winner.id(),
                winner.packId().value(),
                EffectiveGlossaryContextRead.LevelEnum.fromValue(winner.level().token()),
                EffectiveGlossaryContextRead.StateEnum.fromValue(facts.state()),
                facts.required(),
                EffectiveGlossaryContextRead.PolicyEnum.fromValue(winner.policy()),
                EffectiveGlossaryContextRead.ReasonEnum.fromValue(facts.reason()),
                glossaries.context(read.context()),
                links);
        if (!facts.satisfiedBy().isEmpty()) {
            wire.setSatisfiedBy(
                    facts.satisfiedBy().stream().map(EffectiveBodies::reference).toList());
        }
        facts.suggestion().map(EffectiveBodies::suggestion).ifPresent(wire::setSuggestion);
        return wire;
    }

    /**
     * One term of a glossary context under an activation, with the context's facts.
     *
     * @param read what the application ring read
     * @return the {@code EffectiveGlossaryTermRead}
     */
    public EffectiveGlossaryTermRead term(EffectiveGlossaryReads.TermRead read) {
        ActivationArtifacts.Scope scope = read.scope();
        ActivationArtifacts.Facts facts = read.facts();
        ActivationArtifacts.Held winner = facts.held();
        String context = href(ACTIVATIONS, scope.activationId(), ARTIFACTS, GLOSSARY, winner.id());
        String term = read.term().id();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(context + segments(TERMS, term)));
        links.put(ARTIFACT, new Link(packContext(winner, winner.id()) + segments(TERMS, term)));
        links.put(UP, new Link(context));
        EffectiveGlossaryTermRead wire = new EffectiveGlossaryTermRead(
                scope.activationId(),
                scope.revision(),
                scope.governanceFingerprint(),
                EffectiveGlossaryTermRead.KindEnum.GLOSSARY,
                winner.id(),
                winner.packId().value(),
                EffectiveGlossaryTermRead.LevelEnum.fromValue(winner.level().token()),
                EffectiveGlossaryTermRead.StateEnum.fromValue(facts.state()),
                facts.required(),
                EffectiveGlossaryTermRead.PolicyEnum.fromValue(winner.policy()),
                EffectiveGlossaryTermRead.ReasonEnum.fromValue(facts.reason()),
                GlossaryBodies.contextTerm(read.term()),
                links);
        if (!facts.satisfiedBy().isEmpty()) {
            wire.setSatisfiedBy(
                    facts.satisfiedBy().stream().map(EffectiveBodies::reference).toList());
        }
        facts.suggestion().map(EffectiveBodies::suggestion).ifPresent(wire::setSuggestion);
        return wire;
    }

    /** The pack-scoped read of the winning copy of a context. */
    private String packContext(ActivationArtifacts.Held winner, String id) {
        return href(PACKS, winner.packId().value(), ARTIFACTS, GLOSSARY, id);
    }

    private String href(String... segments) {
        return basePath + segments(segments);
    }

    /** The segments, each percent-encoded and led by {@code /}. */
    private static String segments(String... segments) {
        return Arrays.stream(segments)
                .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining());
    }
}
