package ai.speckitty.charter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackSources;
import ai.speckitty.charter.domain.RefusalCode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The read digests twice around the load and refuses on a difference, or on a failed load whose inputs changed. */
class ConsistentLoadTest {

    private static final String A = "sha256:" + "a".repeat(64);
    private static final String B = "sha256:" + "b".repeat(64);
    private static final CharterOffering OFFERING = new CharterOffering(List.of());

    /** Answers the given digests in order; a {@link RuntimeException} in the list is thrown instead. */
    private static PackSources digests(Object... answers) {
        Deque<Object> queue = new ArrayDeque<>(List.of(answers));
        return () -> {
            Object next = queue.removeFirst();
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            return (String) next;
        };
    }

    private static CharterRefusal unreadable() {
        return new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a.graph.yaml is not valid YAML");
    }

    @Test
    void equalDigestsAnswerTheOfferingWithTheVerifiedDigest() {
        ConsistentLoad.Loaded loaded = new ConsistentLoad(() -> OFFERING, digests(A, A)).load();

        assertThat(loaded.offering()).isSameAs(OFFERING);
        assertThat(loaded.sourceFingerprint()).isEqualTo(A);
    }

    @Test
    void differentDigestsAreStaleInput() {
        ConsistentLoad load = new ConsistentLoad(() -> OFFERING, digests(A, B));

        assertThatThrownBy(load::load).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT);
            assertThat(refusal.getMessage())
                    .isEqualTo("The packs or the project configuration changed while the read was answered;"
                            + " read again.");
        });
    }

    @Test
    void aFailedLoadWhoseInputsChangedIsStaleInputNotTheLoadsCode() {
        CharterPackRepository failing = () -> {
            throw unreadable();
        };

        assertThatThrownBy(new ConsistentLoad(failing, digests(A, B))::load)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aFailedLoadWhoseInputsDidNotChangeKeepsTheLoadsRefusal() {
        CharterRefusal original = unreadable();
        CharterPackRepository failing = () -> {
            throw original;
        };

        assertThatThrownBy(new ConsistentLoad(failing, digests(A, A))::load).isSameAs(original);
    }

    @Test
    void aSuccessfulLoadWhoseSecondDigestIsRefusedIsStaleInput() {
        ConsistentLoad load = new ConsistentLoad(() -> OFFERING, digests(A, unreadable()));

        assertThatThrownBy(load::load)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aFailedLoadWhoseRedigestFailsIsStaleInput() {
        CharterPackRepository failing = () -> {
            throw unreadable();
        };

        assertThatThrownBy(new ConsistentLoad(failing, digests(A, unreadable()))::load)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void anUnreadableFirstDigestIsPropagatedAndNothingIsLoaded() {
        CharterRefusal original = unreadable();
        AtomicInteger loads = new AtomicInteger();
        CharterPackRepository counting = () -> {
            loads.incrementAndGet();
            return OFFERING;
        };

        assertThatThrownBy(new ConsistentLoad(counting, digests(original))::load)
                .isSameAs(original);
        assertThat(loads).hasValue(0);
    }

    @Test
    void aFailureThatIsNotARefusalIsNotTurnedIntoOne() {
        IllegalStateException bug = new IllegalStateException("bug");
        CharterPackRepository failing = () -> {
            throw bug;
        };

        assertThatThrownBy(new ConsistentLoad(failing, digests(A))::load).isSameAs(bug);
    }
}
