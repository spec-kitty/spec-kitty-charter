package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Facts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The skill reads under an activation (#107; FR-009 and FR-010, research R1): the skills the activation's closure holds
 * and one skill, each the copy the layer merge keeps, whatever its state. The item is one read of the activations port,
 * which answers the winner's facts and the skill as its pack stores it from one session; the packs port maps it
 * ({@link SkillReads#view}). The list composes two reads: the activation names the winners and the source fingerprint,
 * and each winner's pack is read for the summary against that fingerprint.
 *
 * <p>Refusals come in order: the activations port judges the activation ({@code activation_not_found}), then whether a
 * pack of the closure holds the skill ({@code artifact_not_found}), both before {@code If-None-Match}. A second read at
 * another fingerprint, or of a winner its pack no longer holds, is {@code stale_input}.
 */
@Service
public final class EffectiveSkillReads {

    private final ActivationArtifacts activations;
    private final SkillReads packs;

    /**
     * The reads over the activations port and the packs port.
     *
     * @param activations the port that names the winners under an activation
     * @param packs the port that reads and maps what a pack stores of a skill
     */
    @Autowired
    public EffectiveSkillReads(ActivationArtifacts activations, SkillReads packs) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The skills the activation's closure holds, each the winning copy, in id order, whatever their state.
     *
     * @param activationId the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the skills, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses, or with {@code stale_input}
     */
    public Outcome<Listed> list(String activationId, @Nullable String ifNoneMatch) {
        return switch (activations.winners(activationId, ArtifactKind.SKILL, ifNoneMatch)) {
            case Outcome.NotModified<ActivationArtifacts.Winners> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationArtifacts.Winners> found ->
                new Outcome.Found<>(listed(found.value()), found.etag());
        };
    }

    /**
     * One skill under the activation, whatever its state: its facts and the winning copy.
     *
     * @param activationId the activation's id
     * @param skillId the skill's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the skill, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses
     */
    public Outcome<Read> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
        return switch (activations.skill(activationId, skillId, ifNoneMatch)) {
            case Outcome.NotModified<ActivationArtifacts.SkillWinner> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationArtifacts.SkillWinner> found -> {
                ActivationArtifacts.SkillWinner winner = found.value();
                SkillView skill = packs.view(
                        winner.skill(), "The read of the skill " + skillId + " under activation " + activationId);
                yield new Outcome.Found<>(new Read(winner.scope(), winner.facts(), skill), found.etag());
            }
        };
    }

    /** Each winner with the skill its pack holds, every pack read once against the activation's fingerprint. */
    private Listed listed(ActivationArtifacts.Winners winners) {
        String fingerprint = winners.scope().sourceFingerprint();
        Map<PackIdentifier, Map<String, SkillView>> byPack = new HashMap<>();
        List<Item> items = winners.winners().stream()
                .map(winner -> {
                    Map<String, SkillView> held = byPack.computeIfAbsent(
                            winner.packId(),
                            pack -> WinnerReads.of(() -> packs.list(pack, fingerprint)).skills().stream()
                                    .collect(Collectors.toMap(SkillView::id, Function.identity())));
                    SkillView skill = held.get(winner.id());
                    if (skill == null) {
                        throw WinnerReads.stale();
                    }
                    return new Item(winner, skill);
                })
                .toList();
        return new Listed(winners.scope(), items);
    }

    /**
     * One skill of the list: the winner and the winning copy.
     *
     * @param winner the winner
     * @param skill the winning copy
     */
    public record Item(Held winner, SkillView skill) {

        /**
         * Checks the fields.
         *
         * @param winner the winner
         * @param skill the skill
         */
        public Item {
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(skill, "skill");
        }
    }

    /**
     * What the list answers.
     *
     * @param scope the activation and the read
     * @param items the skills, by id
     */
    public record Listed(Scope scope, List<Item> items) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param items the items
         */
        public Listed {
            Objects.requireNonNull(scope, "scope");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /**
     * What the item answers.
     *
     * @param scope the activation and the read
     * @param facts the winner's facts under the activation
     * @param skill the winning copy
     */
    public record Read(Scope scope, Facts facts, SkillView skill) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param facts the facts
         * @param skill the skill
         */
        public Read {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(skill, "skill");
        }
    }
}
