package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.EffectiveArtifactAnswer;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.api.model.EffectiveArtifactRead;
import ai.speckitty.charter.api.model.EffectiveArtifactSuggestion;
import ai.speckitty.charter.api.model.Link;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of {@code getEffectiveArtifact} (WP06, T033): the winner of one artifact with the facts the effective
 * list gives it, and no body. Its links follow {@link EffectiveReadMapper}'s style: {@code self} is the request that was
 * made (the {@code depth} is repeated only when the request sent one), {@code artifact} is the pack-scoped read of the
 * pack that holds the version in force, and {@code up} is the activation, as an {@code ActivationChange} names it.
 *
 * <p>This mapper builds its own links under the base path the controllers are mapped under and names nothing of the packs
 * adapter: the shared surface between slices is the application layer, not the REST one (decision
 * {@code 01M710DQEMV7VPKDNQPWKPGD4F}).
 */
@Component
public class EffectiveArtifactMapper {

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public EffectiveArtifactMapper(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The winner of one artifact.
     *
     * @param answer what the service read
     * @param depth the {@code depth} the request sent; empty when it sent none, so that {@code self} is the request that
     *     was made
     * @return its wire form
     */
    public EffectiveArtifactRead toRead(EffectiveArtifactAnswer answer, OptionalInt depth) {
        EffectiveArtifact winner = answer.winner();
        String activationId = answer.stored().id().value();
        String kind = winner.kind().token();
        EffectiveArtifactRead wire = new EffectiveArtifactRead(
                activationId,
                answer.stored().revision().token(),
                answer.governanceFingerprint(),
                EffectiveArtifactRead.KindEnum.fromValue(kind),
                winner.artifactId(),
                winner.packId(),
                EffectiveArtifactRead.LevelEnum.fromValue(winner.level().token()),
                EffectiveArtifactRead.StateEnum.fromValue(winner.state().token()),
                winner.required(),
                EffectiveArtifactRead.PolicyEnum.fromValue(winner.policy().token()),
                EffectiveArtifactRead.ReasonEnum.fromValue(winner.reason().token()),
                links(activationId, winner, depth));
        winner.satisfiedBy().map(ActivationChangeMapper::reference).ifPresent(wire::setSatisfiedBy);
        winner.suggestion().ifPresent(suggestion -> {
            EffectiveArtifactSuggestion carrier = new EffectiveArtifactSuggestion();
            suggestion.when().ifPresent(carrier::setWhen);
            suggestion.reason().ifPresent(carrier::setReason);
            wire.setSuggestion(carrier);
        });
        return wire;
    }

    private Map<String, Link> links(String activationId, EffectiveArtifact winner, OptionalInt depth) {
        String kind = winner.kind().token();
        UriComponentsBuilder self = UriComponentsBuilder.fromPath(
                href("activations", activationId, "artifacts", kind, winner.artifactId()));
        depth.ifPresent(value -> self.queryParam("depth", value));
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(self.build(true).toUriString()));
        links.put("artifact", new Link(href("packs", winner.packId(), "artifacts", kind, winner.artifactId())));
        links.put("up", new Link(href("activations", activationId)));
        return links;
    }

    /** The absolute path under the base path, each segment percent-encoded. */
    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
