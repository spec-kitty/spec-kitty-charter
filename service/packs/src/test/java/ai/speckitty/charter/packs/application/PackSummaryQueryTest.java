package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackDocumentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The summary read of a stored pack: what it refuses to summarise is a 404, not a 500 and not an empty summary. */
class PackSummaryQueryTest {

    private final PackDescriptorRepository descriptors = mock(PackDescriptorRepository.class);
    private final PackRevisions revisions = mock(PackRevisions.class);
    private final PackSummaryQuery query = new PackSummaryQuery(
            new ConsistentRead(() -> "sha256:" + "0".repeat(64)),
            () -> List.of(new ConfiguredPack(new PackIdentifier("built-in-pack"), PackLevel.BUILT_IN)),
            descriptors,
            revisions,
            mock(PackDocumentRepository.class));

    @Test
    void aBuiltInPackIsNotFoundBecauseItHasNoRegistrationSummary() {
        assertThatThrownBy(() -> query.summary("built-in-pack"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(refusal.getMessage()).contains("built-in");
                });
        verifyNoInteractions(revisions);
    }
}
