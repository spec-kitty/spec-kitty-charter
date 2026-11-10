package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.EffectiveAgentProfileRead;
import ai.speckitty.charter.api.model.EffectiveArtifactSuggestion;
import ai.speckitty.charter.api.model.EffectiveGovernanceEntry;
import ai.speckitty.charter.api.model.EffectiveGovernanceSuggestion;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.profiles.application.EffectiveProfileRead;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of the profile read under an activation: the facts of the winner as the activation's other reads give
 * them, the profile in {@code agentProfile} (mapped by {@link ProfileDtos}, the same closed schema as the pack-scoped
 * read) and the governance entries, each with the facts of its own winner and a link to the pack-scoped read of it. It is
 * the profiles edge's own and names nothing of the activations adapter or the packs adapter: the links are built under
 * the base path the controllers are mapped under, the way {@link ProfileDtos} and the activations mappers build theirs.
 *
 * <p>The links of the answer are {@code self} (the request that was made: the {@code depth} is repeated only when the
 * request sent one), {@code artifact} (the pack-scoped read of the profile in the pack that holds the version in force)
 * and {@code up} (the activation). An entry links {@code artifact}, the pack-scoped read of its winner, only for a kind
 * that read serves.
 */
@Component
public class EffectiveProfileDtos {

    private static final String ACTIVATIONS = "activations";
    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String DEPTH = "depth";
    private static final String PROFILE = ArtifactKind.AGENT_PROFILE.token();

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public EffectiveProfileDtos(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The answer of a profile read under an activation.
     *
     * @param read what the application ring read
     * @param depth the depth the request named; empty when it named none, so that {@code self} is the request that was
     *     made
     * @return the profile with the facts of its winner and its governance
     */
    public EffectiveAgentProfileRead read(EffectiveProfileRead read, OptionalInt depth) {
        EffectiveProfileRead.Facts winner = read.winner();
        EffectiveAgentProfileRead wire = new EffectiveAgentProfileRead(
                read.activationId(),
                read.revision(),
                read.governanceFingerprint(),
                EffectiveAgentProfileRead.KindEnum.fromValue(winner.urn().kind().token()),
                winner.urn().id(),
                winner.pack().value(),
                EffectiveAgentProfileRead.LevelEnum.fromValue(winner.level().token()),
                EffectiveAgentProfileRead.StateEnum.fromValue(winner.state()),
                winner.required(),
                EffectiveAgentProfileRead.PolicyEnum.fromValue(winner.policy()),
                EffectiveAgentProfileRead.ReasonEnum.fromValue(winner.reason()),
                ProfileDtos.profile(read.profile()),
                read.governance().stream().map(entry -> entry(entry, read)).toList(),
                links(read, depth));
        winner.satisfiedBy().map(EffectiveProfileDtos::reference).ifPresent(wire::setSatisfiedBy);
        winner.suggestion().map(EffectiveProfileDtos::suggestion).ifPresent(wire::setSuggestion);
        return wire;
    }

    private Map<String, Link> links(EffectiveProfileRead read, OptionalInt depth) {
        String id = read.winner().urn().id();
        String self = href(ACTIVATIONS, read.activationId(), ARTIFACTS, PROFILE, id);
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(depth.isPresent() ? self + "?" + DEPTH + "=" + depth.getAsInt() : self));
        links.put("artifact", new Link(href(PACKS, read.winner().pack().value(), ARTIFACTS, PROFILE, id)));
        links.put("up", new Link(href(ACTIVATIONS, read.activationId())));
        return links;
    }

    private EffectiveGovernanceEntry entry(EffectiveProfileRead.Entry entry, EffectiveProfileRead read) {
        EffectiveProfileRead.Facts facts = entry.facts();
        Map<String, Link> links = new LinkedHashMap<>();
        if (read.linksArtifact(entry)) {
            links.put(
                    "artifact",
                    new Link(href(
                            PACKS,
                            facts.pack().value(),
                            ARTIFACTS,
                            facts.urn().kind().token(),
                            facts.urn().id())));
        }
        EffectiveGovernanceEntry wire = new EffectiveGovernanceEntry(
                EffectiveGovernanceEntry.KindEnum.fromValue(facts.urn().kind().token()),
                facts.urn().id(),
                facts.pack().value(),
                EffectiveGovernanceEntry.LevelEnum.fromValue(facts.level().token()),
                EffectiveGovernanceEntry.StateEnum.fromValue(facts.state()),
                facts.required(),
                EffectiveGovernanceEntry.PolicyEnum.fromValue(facts.policy()),
                EffectiveGovernanceEntry.ReasonEnum.fromValue(facts.reason()),
                EffectiveGovernanceEntry.StepEnum.fromValue(entry.step().token()),
                EffectiveGovernanceEntry.RelationEnum.fromValue(entry.relation().token()),
                entry.via().toString(),
                entry.hops(),
                links);
        facts.satisfiedBy().map(EffectiveProfileDtos::reference).ifPresent(wire::setSatisfiedBy);
        facts.suggestion().map(EffectiveProfileDtos::edgeWords).ifPresent(wire::setSuggestion);
        return wire;
    }

    private static ArtifactReference reference(ai.speckitty.charter.kernel.domain.Urn urn) {
        return new ArtifactReference(
                ArtifactReference.KindEnum.fromValue(urn.kind().token()), urn.id());
    }

    private static EffectiveArtifactSuggestion suggestion(
            ai.speckitty.charter.profiles.application.EdgeWords suggestion) {
        EffectiveArtifactSuggestion carrier = new EffectiveArtifactSuggestion();
        suggestion.when().ifPresent(carrier::setWhen);
        suggestion.reason().ifPresent(carrier::setReason);
        return carrier;
    }

    /** The words of the suggests edge that listed an entry, whether the entry is in force or not. */
    private static EffectiveGovernanceSuggestion edgeWords(
            ai.speckitty.charter.profiles.application.EdgeWords suggestion) {
        EffectiveGovernanceSuggestion carrier = new EffectiveGovernanceSuggestion();
        suggestion.when().ifPresent(carrier::setWhen);
        suggestion.reason().ifPresent(carrier::setReason);
        return carrier;
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
