package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ServiceInfoQueryTest {

    @Test
    void answersTheWorktreeAndTheCurrentSourceFingerprint() {
        ServiceInfoQuery query = new ServiceInfoQuery("wt-1", () -> "sha256:" + "0".repeat(64));
        assertThat(query.answer()).isEqualTo(new ServiceInfo("wt-1", "sha256:" + "0".repeat(64)));
        assertThat(query.worktree()).isEqualTo("wt-1");
    }

    @Test
    void anUnreadablePackRefusesTheAnswerButNotTheWorktree() {
        ServiceInfoQuery query = new ServiceInfoQuery("wt-1", () -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack qa is not a directory");
        });
        assertThat(query.worktree()).isEqualTo("wt-1");
        assertThatThrownBy(query::answer)
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void digestsTwiceAndAnswersWhenBothAgree() {
        AtomicInteger digests = new AtomicInteger();
        ServiceInfoQuery query = new ServiceInfoQuery("wt-1", () -> {
            digests.incrementAndGet();
            return "sha256:" + "1".repeat(64);
        });

        assertThat(query.answer().sourceFingerprint()).isEqualTo("sha256:" + "1".repeat(64));
        assertThat(digests).hasValue(2);
    }

    @Test
    void refusesStaleInputWhenTheTwoDigestsDiffer() {
        Iterator<String> digests =
                List.of("sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64)).iterator();
        ServiceInfoQuery query = new ServiceInfoQuery("wt-1", digests::next);

        assertThatThrownBy(query::answer)
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT))
                .hasMessage("The inputs changed while the service info was read; read it again.");
    }

    @Test
    void aSecondDigestThatCannotBeReadIsAChangeNotAPermanentRefusal() {
        AtomicInteger digests = new AtomicInteger();
        ServiceInfoQuery query = new ServiceInfoQuery("wt-1", () -> {
            if (digests.incrementAndGet() == 1) {
                return "sha256:" + "1".repeat(64);
            }
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack file became unreadable");
        });

        assertThatThrownBy(query::answer)
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }
}
