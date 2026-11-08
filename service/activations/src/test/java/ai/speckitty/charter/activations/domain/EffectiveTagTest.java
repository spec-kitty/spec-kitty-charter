package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveTag.Query;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The entity tag of the reads that depend on the packs of the closure: the revision, the fingerprint of the packs in
 * the closure and the normalized query decide it, and nothing else does (T123, research R5).
 */
class EffectiveTagTest {

    private static final Revision REVISION =
            ActivationDocument.builder().build().document().revision();
    private static final Query FIRST_PAGE = new Query(Optional.empty(), Optional.empty(), Optional.empty(), 100);

    private static ClosureSnapshot closure(String closureFingerprint) {
        return ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .fingerprint("sha256:every-configured-pack")
                .closureFingerprint(closureFingerprint)
                .build();
    }

    @Test
    void theSameInputsGiveTheSameTag() {
        EffectiveTag first = EffectiveTag.artifacts(REVISION, closure("sha256:one"), FIRST_PAGE);
        EffectiveTag again = EffectiveTag.artifacts(
                REVISION, closure("sha256:one"), new Query(Optional.empty(), Optional.empty(), Optional.empty(), 100));

        assertThat(again).isEqualTo(first);
        assertThat(first.token()).matches("sha256:[0-9a-f]{64}");
        assertThat(first.token()).as("a digest, not a placeholder").isNotEqualTo("sha256:" + "0".repeat(64));
    }

    @Test
    void aChangedPackOfTheClosureGivesANewTag() {
        EffectiveTag before = EffectiveTag.artifacts(REVISION, closure("sha256:one"), FIRST_PAGE);
        EffectiveTag after = EffectiveTag.artifacts(REVISION, closure("sha256:two"), FIRST_PAGE);

        assertThat(after).isNotEqualTo(before);
        assertThat(EffectiveTag.activation(REVISION, closure("sha256:two")))
                .isNotEqualTo(EffectiveTag.activation(REVISION, closure("sha256:one")));
        assertThat(EffectiveTag.skipped(REVISION, closure("sha256:two")))
                .isNotEqualTo(EffectiveTag.skipped(REVISION, closure("sha256:one")));
    }

    @Test
    void aChangedPackOutsideTheClosureLeavesTheTagAsItIs() {
        ClosureSnapshot before = closure("sha256:one");
        ClosureSnapshot after = ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .fingerprint("sha256:a-pack-outside-the-closure-changed")
                .closureFingerprint("sha256:one")
                .build();

        assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
        assertThat(EffectiveTag.artifacts(REVISION, after, FIRST_PAGE))
                .as("the tag covers the digest of the closure's own packs, not the one of every configured pack")
                .isEqualTo(EffectiveTag.artifacts(REVISION, before, FIRST_PAGE));
        assertThat(EffectiveTag.activation(REVISION, after)).isEqualTo(EffectiveTag.activation(REVISION, before));
        assertThat(EffectiveTag.skipped(REVISION, after)).isEqualTo(EffectiveTag.skipped(REVISION, before));
    }

    @Test
    void eachQueryVariantHasItsOwnTag() {
        ClosureSnapshot closure = closure("sha256:one");
        List<Query> variants = List.of(
                FIRST_PAGE,
                new Query(Optional.of(State.ACTIVE), Optional.empty(), Optional.empty(), 100),
                new Query(Optional.of(State.INACTIVE), Optional.empty(), Optional.empty(), 100),
                new Query(Optional.empty(), Optional.of(ArtifactKind.DIRECTIVE), Optional.empty(), 100),
                new Query(Optional.empty(), Optional.of(ArtifactKind.TACTIC), Optional.empty(), 100),
                new Query(Optional.empty(), Optional.empty(), Optional.of("c1"), 100),
                new Query(Optional.empty(), Optional.empty(), Optional.of("c2"), 100),
                new Query(Optional.empty(), Optional.empty(), Optional.empty(), 50),
                new Query(Optional.of(State.ACTIVE), Optional.of(ArtifactKind.DIRECTIVE), Optional.of("c1"), 50));

        Set<EffectiveTag> tags = new HashSet<>();
        variants.forEach(query -> tags.add(EffectiveTag.artifacts(REVISION, closure, query)));

        assertThat(tags).as("no two representations share a tag").hasSize(variants.size());
    }

    @Test
    void aFilterThatMovesBetweenFieldsIsAnotherRepresentation() {
        ClosureSnapshot closure = closure("sha256:one");

        assertThat(EffectiveTag.artifacts(
                        REVISION, closure, new Query(Optional.empty(), Optional.empty(), Optional.of("active"), 100)))
                .isNotEqualTo(EffectiveTag.artifacts(
                        REVISION,
                        closure,
                        new Query(Optional.of(State.ACTIVE), Optional.empty(), Optional.empty(), 100)));
    }

    @Test
    void theV1ToV2WriteGivesANewTag() {
        Revision v1 = Revision.of("{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8));
        ClosureSnapshot closure = closure("sha256:one");

        assertThat(v1).isNotEqualTo(REVISION);
        assertThat(EffectiveTag.artifacts(REVISION, closure, FIRST_PAGE))
                .isNotEqualTo(EffectiveTag.artifacts(v1, closure, FIRST_PAGE));
        assertThat(EffectiveTag.activation(REVISION, closure)).isNotEqualTo(EffectiveTag.activation(v1, closure));
    }

    @Test
    void theReadsOfOneActivationAndClosureNeverShareATag() {
        ClosureSnapshot closure = closure("sha256:one");

        Set<EffectiveTag> tags = new HashSet<>(List.of(
                EffectiveTag.activation(REVISION, closure),
                EffectiveTag.skipped(REVISION, closure),
                EffectiveTag.artifacts(REVISION, closure, FIRST_PAGE)));

        assertThat(tags)
                .as("getActivation, the skipped report and the unfiltered list")
                .hasSize(3);
    }
}
