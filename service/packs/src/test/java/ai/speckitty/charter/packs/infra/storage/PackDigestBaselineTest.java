package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * NFR-001: a pack that holds no {@code enhancements/} directory keeps the source fingerprint it had before the
 * step-enhancement binding existed. The invariant is computed live: for every pack of the checkout that holds no
 * {@code enhancements/} directory, the digest with every kind bound ({@code digestOfPack}) equals the digest of the
 * files that remain when the {@link ArtifactKind#STEP_ENHANCEMENT} binding is left out. A pack that holds step
 * enhancements is not covered; there the binding must move the digest, which the test also asserts, so a binding that
 * digested nothing could not pass.
 *
 * <p>No digest of the checkout is frozen here, so a move of the open-packs pin needs no recapture. This check is
 * relative: both sides go through the same digest code, so it cannot see a change to the hash, the encoding or the
 * order of the kinds. {@link PackDigestFixtureTest} guards those with a fixed fixture pack and a frozen fingerprint,
 * which a pin move never touches. If this goes red, the binding changed the digest of a pack without step
 * enhancements: fix the code (append, never insert).
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Packs without step enhancements keep their fingerprint (NFR-001)")
class PackDigestBaselineTest {

    private static final Set<ArtifactKind> WITHOUT_STEP_ENHANCEMENTS = withoutStepEnhancements();

    private static Set<ArtifactKind> withoutStepEnhancements() {
        Set<ArtifactKind> kinds = EnumSet.copyOf(KindBindings.kinds());
        kinds.remove(ArtifactKind.STEP_ENHANCEMENT);
        return kinds;
    }

    /** Fail with the pin's own message rather than as a digest mismatch when the checkout is at another commit. */
    @BeforeAll
    static void theCheckoutIsAtThePin() {
        OpenPacksCorpus.assertPinned();
    }

    @Test
    void everyPackWithoutEnhancementsKeepsItsDigest() throws IOException {
        List<String> covered = new ArrayList<>();
        for (PackLocation pack : OpenPacksCorpus.packs()) {
            List<String> withoutBinding = PackFileSet.digested(pack.root(), WITHOUT_STEP_ENHANCEMENTS);
            if (holdsEnhancements(pack.root())) {
                assertThat(PackFileSet.digested(pack.root()))
                        .as("%s: the binding digests the step enhancements the pack holds", pack.name())
                        .containsAll(withoutBinding)
                        .hasSizeGreaterThan(withoutBinding.size());
            } else {
                covered.add(pack.name());
                assertThat(FileSystemPackSources.digestOfPack(pack.root(), pack.name()))
                        .as("%s: binding step enhancements moved the digest of a pack that has none", pack.name())
                        .isEqualTo(FileSystemPackSources.digest(List.of(pack), root -> withoutBinding));
            }
        }

        OpenPacksCorpus.assertAllPacksPresent();
        assertThat(covered)
                .as("every pack but the one that holds step enhancements is covered")
                .contains("built-in", "cave-kitty", "event-driven", "internal", "qa");
    }

    private static boolean holdsEnhancements(Path pack) throws IOException {
        try (Stream<Path> walk = Files.walk(pack)) {
            return walk.anyMatch(path ->
                    Files.isDirectory(path) && path.getFileName().toString().equals("enhancements"));
        }
    }
}
