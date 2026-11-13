package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.AssetList;
import ai.speckitty.charter.api.model.AssetSummary;
import ai.speckitty.charter.api.model.EffectiveArtifactSuggestion;
import ai.speckitty.charter.api.model.EffectiveAssetRead;
import ai.speckitty.charter.api.model.EffectiveSkillRead;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.SkillList;
import ai.speckitty.charter.api.model.SkillSummary;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.EffectiveAssetReads;
import ai.speckitty.charter.artifacts.application.EffectiveSkillReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.Urn;
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
 * The wire form of the asset and skill reads under an activation (FR-009, research R1 and R2): the facts the activation
 * gives the winner and the member as the pack-scoped reads map it ({@link AssetBodies#asset}, {@link SkillBodies#skill}),
 * so a descriptor and a skill are mapped once in the edge (research R13). The links start at the base path the
 * controllers are mapped under.
 *
 * <ul>
 *   <li>A list links {@code self} and {@code up} (the activation); each item links {@code effective-artifact}, its read
 *       under the activation, and {@code artifact}, the pack-scoped read of the winning copy, as an item of the
 *       effective list does.
 *   <li>An asset links {@code self}, {@code artifact}, {@code payload} (its bytes under the activation),
 *       {@code collection} (the activation's assets) and {@code up}.
 *   <li>A skill links {@code self}, {@code artifact}, {@code collection} (the activation's skills) and {@code up}.
 * </ul>
 */
@Component
public class EffectiveBodies {

    private static final String ACTIVATIONS = "activations";
    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String PAYLOAD = "payload";
    private static final String SELF = "self";
    private static final String UP = "up";
    private static final String ARTIFACT = "artifact";
    private static final String COLLECTION = "collection";
    private static final String EFFECTIVE_ARTIFACT = "effective-artifact";
    private static final String ASSET = ArtifactKind.ASSET.token();
    private static final String SKILL = ArtifactKind.SKILL.token();

    private final String basePath;
    private final AssetBodies assets;
    private final SkillBodies skills;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     * @param assets the mapper of an asset's descriptor
     * @param skills the mapper of a skill
     */
    @Autowired
    public EffectiveBodies(
            @Value("${charter.api.base-path:/api/v1}") String basePath, AssetBodies assets, SkillBodies skills) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.assets = Objects.requireNonNull(assets, "assets");
        this.skills = Objects.requireNonNull(skills, "skills");
    }

    /**
     * The assets of an activation.
     *
     * @param listed what the application ring read
     * @return the {@code AssetList}
     */
    public AssetList assets(EffectiveAssetReads.Listed listed) {
        String activation = listed.scope().activationId();
        List<AssetSummary> items = listed.items().stream()
                .map(item -> new AssetSummary(
                        item.asset().id(),
                        item.asset().mime(),
                        item.asset().executable().isPresent(),
                        itemLinks(activation, ASSET, item.winner())))
                .toList();
        return new AssetList(items, listLinks(activation, ASSET));
    }

    /**
     * One asset under an activation.
     *
     * @param read what the application ring read
     * @return the {@code EffectiveAssetRead}
     */
    public EffectiveAssetRead asset(EffectiveAssetReads.Read read) {
        ActivationArtifacts.Scope scope = read.scope();
        ActivationArtifacts.Held winner = read.winner();
        String self = href(ACTIVATIONS, scope.activationId(), ARTIFACTS, ASSET, winner.id());
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(self));
        links.put(ARTIFACT, new Link(href(PACKS, winner.packId().value(), ARTIFACTS, ASSET, winner.id())));
        links.put(PAYLOAD, new Link(self + "/" + PAYLOAD));
        links.put(COLLECTION, new Link(href(ACTIVATIONS, scope.activationId(), ARTIFACTS, ASSET)));
        links.put(UP, new Link(href(ACTIVATIONS, scope.activationId())));
        return new EffectiveAssetRead(
                scope.activationId(),
                scope.revision(),
                scope.governanceFingerprint(),
                EffectiveAssetRead.KindEnum.ASSET,
                winner.id(),
                winner.packId().value(),
                EffectiveAssetRead.LevelEnum.fromValue(winner.level().token()),
                EffectiveAssetRead.PolicyEnum.fromValue(winner.policy()),
                assets.asset(read.asset()),
                links);
    }

    /**
     * The skills of an activation.
     *
     * @param listed what the application ring read
     * @return the {@code SkillList}
     */
    public SkillList skills(EffectiveSkillReads.Listed listed) {
        String activation = listed.scope().activationId();
        List<SkillSummary> items = listed.items().stream()
                .map(item -> new SkillSummary(
                        item.skill().id(),
                        item.skill().title(),
                        item.skill().description(),
                        SkillSummary.FormEnum.fromValue(item.skill().form()),
                        itemLinks(activation, SKILL, item.winner())))
                .toList();
        return new SkillList(items, listLinks(activation, SKILL));
    }

    /**
     * One skill under an activation.
     *
     * @param read what the application ring read
     * @return the {@code EffectiveSkillRead}
     */
    public EffectiveSkillRead skill(EffectiveSkillReads.Read read) {
        ActivationArtifacts.Scope scope = read.scope();
        ActivationArtifacts.Facts facts = read.facts();
        ActivationArtifacts.Held winner = facts.held();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(href(ACTIVATIONS, scope.activationId(), ARTIFACTS, SKILL, winner.id())));
        links.put(ARTIFACT, new Link(href(PACKS, winner.packId().value(), ARTIFACTS, SKILL, winner.id())));
        links.put(COLLECTION, new Link(href(ACTIVATIONS, scope.activationId(), ARTIFACTS, SKILL)));
        links.put(UP, new Link(href(ACTIVATIONS, scope.activationId())));
        EffectiveSkillRead wire = new EffectiveSkillRead(
                scope.activationId(),
                scope.revision(),
                scope.governanceFingerprint(),
                EffectiveSkillRead.KindEnum.SKILL,
                winner.id(),
                winner.packId().value(),
                EffectiveSkillRead.LevelEnum.fromValue(winner.level().token()),
                EffectiveSkillRead.StateEnum.fromValue(facts.state()),
                facts.required(),
                EffectiveSkillRead.PolicyEnum.fromValue(winner.policy()),
                EffectiveSkillRead.ReasonEnum.fromValue(facts.reason()),
                skills.skill(read.skill()),
                links);
        facts.satisfiedBy().map(EffectiveBodies::reference).ifPresent(wire::setSatisfiedBy);
        facts.suggestion().map(EffectiveBodies::suggestion).ifPresent(wire::setSuggestion);
        return wire;
    }

    private Map<String, Link> itemLinks(String activation, String kind, ActivationArtifacts.Held winner) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(EFFECTIVE_ARTIFACT, new Link(href(ACTIVATIONS, activation, ARTIFACTS, kind, winner.id())));
        links.put(ARTIFACT, new Link(href(PACKS, winner.packId().value(), ARTIFACTS, kind, winner.id())));
        return links;
    }

    private Map<String, Link> listLinks(String activation, String kind) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(href(ACTIVATIONS, activation, ARTIFACTS, kind)));
        links.put(UP, new Link(href(ACTIVATIONS, activation)));
        return links;
    }

    private static ArtifactReference reference(Urn urn) {
        return new ArtifactReference(
                ArtifactReference.KindEnum.fromValue(urn.kind().token()), urn.id());
    }

    private static EffectiveArtifactSuggestion suggestion(ActivationArtifacts.Words words) {
        EffectiveArtifactSuggestion carrier = new EffectiveArtifactSuggestion();
        words.when().ifPresent(carrier::setWhen);
        words.reason().ifPresent(carrier::setReason);
        return carrier;
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
