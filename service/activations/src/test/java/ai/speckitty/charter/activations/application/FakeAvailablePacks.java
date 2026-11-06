package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An {@link AvailablePacks} that answers one snapshot a test sets, or one refusal, and counts the calls, so a test
 * pins that a use case reads the packs exactly as often as NFR-007 allows. It records the roots of each call.
 */
public final class FakeAvailablePacks implements AvailablePacks {

    private ClosureSnapshot snapshot;
    private @Nullable CharterRefusal refusal;
    private final List<Optional<List<String>>> calls = new ArrayList<>();

    /**
     * A fake that answers the snapshot.
     *
     * @param snapshot the answer of every call
     */
    public FakeAvailablePacks(ClosureSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Answers another snapshot from now on.
     *
     * @param next the answer
     */
    public void answer(ClosureSnapshot next) {
        this.snapshot = next;
        this.refusal = null;
    }

    /**
     * Refuses every call from now on, as the compose would.
     *
     * @param next the refusal
     */
    public void refuse(CharterRefusal next) {
        this.refusal = next;
    }

    @Override
    public ClosureSnapshot snapshot(Optional<List<String>> packIds) {
        calls.add(packIds);
        if (refusal != null) {
            throw refusal;
        }
        return snapshot;
    }

    /**
     * How often the packs were read.
     *
     * @return the number of {@link #snapshot} calls
     */
    public int calls() {
        return calls.size();
    }

    /**
     * The roots of the last call.
     *
     * @return the roots; empty for every configured pack
     */
    public Optional<List<String>> lastRoots() {
        return calls.getLast();
    }
}
