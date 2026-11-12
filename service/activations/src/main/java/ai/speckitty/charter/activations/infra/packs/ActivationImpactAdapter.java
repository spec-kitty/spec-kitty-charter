package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.ActivationImpact;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The activations slice, as the registration impact check of {@code packs} asks it: the one implementation of
 * {@link ActivationImpact}. It names only {@code packs.application}, as the rest of this adapter package does
 * ({@code HexagonalArchitectureTest} pins it).
 *
 * <p>{@link #activationRoots()} is the {@code packs} list of every stored activation that reads, as the pack ids it names
 * in the order written, or empty for an activation that lists none, which roots at every configured pack.
 * {@link #activationsAffected} is the affected-activations notice (FR-027, B4): given the closures a registration
 * composed, before and after, it asks the in-force resolution of each activation whose closure holds the pack, over both
 * closures, and names the activations whose effective set differs: the artifacts in force, or the version in force of one
 * of them (its pack, level or policy). {@link #requireComposable} is the registration gate of C38 over the same
 * closures; unlike the notices it refuses. It composes nothing and reads no pack: the
 * closures are the impact check's own, and an activation whose closure does not hold the pack is not asked. A closure that
 * cannot be merged has no set in force, which differs from one that can be and equals another that cannot, so a
 * registration that makes an activation unreadable names it and one that leaves it unreadable does not.
 */
@Component
public class ActivationImpactAdapter implements ActivationImpact {

    private static final Logger LOG = LoggerFactory.getLogger(ActivationImpactAdapter.class);

    private final ActivationRepository repository;

    /**
     * The adapter over the activation store.
     *
     * @param repository the store of the activation documents
     */
    @Autowired
    public ActivationImpactAdapter(ActivationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<Optional<List<String>>> activationRoots() {
        return stored().stream().map(ActivationImpactAdapter::rootsOf).toList();
    }

    @Override
    public List<String> activationsAffected(ClosureChanges closures) {
        return stored().stream()
                .filter(activation -> closures.of(rootsOf(activation))
                        .filter(change -> differs(change, activation))
                        .isPresent())
                .map(activation -> activation.id().value())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The registration gate of C38 (squad A2): for each stored activation whose closure the write joins, in id order,
     * the in-force resolution of its document over the closure as the write leaves it, and the slot check over it
     * ({@link ClosureView#requireSlots}). The first {@code prompt_slot_missing} refuses the write, the activation named in
     * the detail; it is never swallowed. An activation whose closure had no view before the write, because it could not
     * be composed or held none of its packs, is not judged: a failure it already had is not blamed on the write (G21).
     * A closure whose merge refuses after the write is judged at read, as the impact check judges a layer override (G8),
     * so the gate leaves it to the reads.
     *
     * @param closures the closures the write joins
     * @throws CharterRefusal with {@code PROMPT_SLOT_MISSING} for the first activation the write leaves without a slot
     */
    @Override
    public void requireComposable(ClosureChanges closures) {
        stored().stream()
                .sorted(java.util.Comparator.comparing(
                        activation -> activation.id().value()))
                .forEach(activation ->
                        closures.of(rootsOf(activation)).ifPresent(change -> requireComposable(change, activation)));
    }

    private static void requireComposable(ClosureChange change, StoredActivation activation) {
        if (change.before().isEmpty()) {
            return;
        }
        InForceRequest request = ClosureViewSession.requestOf(EffectiveSet.queryOf(activation.document()));
        ClosureView after = change.after();
        InForceStates states;
        try {
            states = after.inForce(request);
        } catch (CharterRefusal unmergeable) {
            return;
        }
        try {
            after.requireSlots(states);
        } catch (CharterRefusal missing) {
            throw new CharterRefusal(
                    missing.code(),
                    "Under the activation `" + activation.id().value() + "`: " + String.valueOf(missing.getMessage()),
                    missing.packs());
        }
    }

    /**
     * Whether the activation has another effective set over the closure after the write than before it: another set of
     * artifacts in force, or the version in force of one of them (its pack, level or policy) another.
     */
    private static boolean differs(ClosureChange change, StoredActivation activation) {
        InForceRequest request = ClosureViewSession.requestOf(EffectiveSet.queryOf(activation.document()));
        Optional<Map<ArtifactRef, Optional<MergedSnapshot.Winner>>> before =
                change.before().map(view -> inForce(view, request)).orElseGet(() -> Optional.of(Map.of()));
        return !before.equals(inForce(change.after(), request));
    }

    /**
     * The artifacts the request puts in force over the closure, each with the version in force; empty when the closure
     * cannot be merged.
     */
    private static Optional<Map<ArtifactRef, Optional<MergedSnapshot.Winner>>> inForce(
            ClosureView view, InForceRequest request) {
        try {
            Map<ArtifactRef, MergedSnapshot.Winner> winners =
                    ClosureViewSession.winnersOf(view.merged().closure());
            Map<ArtifactRef, Optional<MergedSnapshot.Winner>> active = new TreeMap<>();
            ClosureViewSession.inForceOf(view.inForce(request)).states().forEach((ref, state) -> {
                if (state.state() == EffectiveSet.State.ACTIVE) {
                    active.put(ref, Optional.ofNullable(winners.get(ref)));
                }
            });
            return Optional.of(active);
        } catch (CharterRefusal unmergeable) {
            return Optional.empty();
        }
    }

    private static Optional<List<String>> rootsOf(StoredActivation activation) {
        return activation
                .document()
                .packs()
                .map(packs -> packs.stream().map(PackRef::packId).toList());
    }

    private List<StoredActivation> stored() {
        try {
            return repository.list();
        } catch (RuntimeException unlistable) {
            // No refusal code names a fault of the activation store, so the write or the registration job ends as
            // internal_error, as a document the store cannot read back does (ADR-0065, DIV-614); this is its log line.
            LOG.error(
                    "The registration impact check cannot list the stored activations; the write is answered"
                            + " internal_error and nothing is written.",
                    unlistable);
            throw unlistable;
        }
    }
}
