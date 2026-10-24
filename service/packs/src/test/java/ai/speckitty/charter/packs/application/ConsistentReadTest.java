package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** One test per row of the refusal-outcome table: what a verified read answers for each digest/load combination. */
class ConsistentReadTest {

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);
    private static final String STALE = "The inputs changed while the thing was read; read it again.";

    private final List<String> events = new ArrayList<>();

    /** A port that logs each digest and answers the scripted outcomes in order: a digest string or a failure. */
    private ConsistentRead reading(Object... digests) {
        Iterator<Object> script = List.of(digests).iterator();
        PackSources sources = () -> {
            events.add("digest");
            Object next = script.next();
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            return (String) next;
        };
        return new ConsistentRead(sources);
    }

    private <T> Supplier<T> loading(Supplier<T> load) {
        return () -> {
            events.add("load");
            return load.get();
        };
    }

    private static CharterRefusal refusal(RefusalCode code) {
        return new CharterRefusal(code, "refused " + code);
    }

    @Test
    void aFirstDigestThatRefusesIsTheAnswerAndTheLoadNeverRuns() {
        CharterRefusal unreadable = refusal(RefusalCode.PACK_UNREADABLE);

        assertThatThrownBy(() -> reading(unreadable).read(STALE, loading(() -> "value")))
                .isSameAs(unreadable);
        assertThat(events).containsExactly("digest");
    }

    @Test
    void equalDigestsAnswerTheValueWithTheDigestTheyAgreeOn() {
        ConsistentRead.Verified<String> verified = reading(D1, D1).read(STALE, loading(() -> "value"));

        assertThat(verified).isEqualTo(new ConsistentRead.Verified<>("value", D1));
        assertThat(events).containsExactly("digest", "load", "digest");
    }

    @Test
    void aSecondDigestThatDiffersIsStale() {
        assertThatThrownBy(() -> reading(D1, D2).read(STALE, loading(() -> "value")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage(STALE);
        assertThat(events).containsExactly("digest", "load", "digest");
    }

    @Test
    void aSecondDigestThatRefusesIsStale() {
        assertThatThrownBy(
                        () -> reading(D1, refusal(RefusalCode.PACK_UNREADABLE)).read(STALE, loading(() -> "value")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage(STALE);
    }

    @Test
    void aLoadRefusalWithEqualDigestsIsTheAnswerItself() {
        CharterRefusal notFound = refusal(RefusalCode.TACTIC_NOT_FOUND);

        assertThatThrownBy(() -> reading(D1, D1).read(STALE, loading(() -> {
                    throw notFound;
                })))
                .isSameAs(notFound);
        assertThat(events).containsExactly("digest", "load", "digest");
    }

    @Test
    void aLoadRefusalWithADifferentSecondDigestIsStaleAndKeepsTheRefusalAsCause() {
        CharterRefusal notFound = refusal(RefusalCode.TACTIC_NOT_FOUND);

        assertThatThrownBy(() -> reading(D1, D2).read(STALE, loading(() -> {
                    throw notFound;
                })))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage(STALE)
                .hasCause(notFound);
    }

    @Test
    void aLoadRefusalWithASecondDigestThatRefusesIsStale() {
        CharterRefusal notFound = refusal(RefusalCode.TACTIC_NOT_FOUND);

        assertThatThrownBy(
                        () -> reading(D1, refusal(RefusalCode.PACK_UNREADABLE)).read(STALE, loading(() -> {
                            throw notFound;
                        })))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasCause(notFound);
    }

    @Test
    void aNonRefusalLoadFailureWithEqualDigestsIsRethrownUnchanged() {
        IllegalStateException broken = new IllegalStateException("the load broke");

        assertThatThrownBy(() -> reading(D1, D1).read(STALE, loading(() -> {
                    throw broken;
                })))
                .isSameAs(broken);
    }

    @Test
    void aNonRefusalFailureOfTheSecondDigestPropagatesAndIsNotStale() {
        IllegalStateException broken = new IllegalStateException("the digest broke");

        assertThatThrownBy(() -> reading(D1, broken).read(STALE, loading(() -> "value")))
                .isSameAs(broken);
        assertThat(events).containsExactly("digest", "load", "digest");
    }

    @Test
    void aNonRefusalFailureOfTheSecondDigestAfterALoadFailurePropagates() {
        IllegalStateException broken = new IllegalStateException("the digest broke");

        assertThatThrownBy(() -> reading(D1, broken).read(STALE, loading(() -> {
                    throw refusal(RefusalCode.TACTIC_NOT_FOUND);
                })))
                .isSameAs(broken);
    }
}
