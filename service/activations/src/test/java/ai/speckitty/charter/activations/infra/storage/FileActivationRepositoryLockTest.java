package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationId;
import org.junit.jupiter.api.Test;

/**
 * The per-id locks are striped over the encoded directory name, so ids that differ only in case are locked by the
 * names they are stored under. That a lock serialises two writers of one id is {@code ActivationServiceRaceTest}'s,
 * which fails on a store without it.
 */
class FileActivationRepositoryLockTest {

    @Test
    void caseVariantsAreLockedByTheirEncodedNames() {
        assertThat(FileActivationRepository.stripeOf(new ActivationId("ABC")))
                .isEqualTo(Math.floorMod("abc-7".hashCode(), FileActivationRepository.LOCK_STRIPES));
        assertThat(FileActivationRepository.stripeOf(new ActivationId("abc")))
                .isEqualTo(Math.floorMod("abc-0".hashCode(), FileActivationRepository.LOCK_STRIPES));
    }
}
