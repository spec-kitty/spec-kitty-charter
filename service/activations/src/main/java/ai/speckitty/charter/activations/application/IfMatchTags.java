package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.transport.EntityTags;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What an activation write's {@code If-Match} names (operator decision {@code 01M6Y3GZFRC5WEQQ7G6GT0YMW3}), judged in
 * this one place for the register's writes ({@link ActivationService}) and for the sub-resource writes
 * ({@link ActivationWrites}): the quoted revision of the stored document, or the current {@code getActivation} tag
 * ({@link EffectiveTag#activation}), so a client may send what it read. {@code *} names any stored document. A closure
 * tag that no longer matches, because the document or a pack of its closure changed, names nothing, and the write is
 * {@code 412}.
 *
 * <p>A revision, {@code *} and a header that holds no {@code sha256:} tag are judged without a read of the packs. A
 * header that holds one and is not the revision costs one verified read of the closure of the stored document, which
 * computes the tag; a closure that cannot be read, or that
 * names a pack no longer configured, has no current tag, so the header names nothing. The write that follows reads the
 * packs again for its own judgment, as it always did.
 */
final class IfMatchTags {

    /** The shape of a tag the service answers: a header that holds none cannot be a closure tag, so it costs no read. */
    private static final Pattern TAG_SHAPED = Pattern.compile("sha256:[0-9a-f]{64}");

    private IfMatchTags() {}

    /**
     * Whether an {@code If-Match} names the stored activation as it is now.
     *
     * @param ifMatch the header, verbatim
     * @param stored the stored activation
     * @param packs the packs, read once when the header is not the revision
     * @return {@code true} when it matches the revision, the current closure tag or {@code *}
     */
    static boolean names(@Nullable String ifMatch, StoredActivation stored, AvailablePacks packs) {
        Objects.requireNonNull(stored, "stored");
        if (EntityTags.matches(ifMatch, stored.revision().token())) {
            return true;
        }
        if (ifMatch == null || !TAG_SHAPED.matcher(ifMatch).find()) {
            return false;
        }
        return closureTag(stored, packs)
                .filter(tag -> EntityTags.matches(ifMatch, tag.token()))
                .isPresent();
    }

    /**
     * The {@code getActivation} tag of a stored activation now.
     *
     * @param stored the stored activation
     * @param packs the packs, read once
     * @return the tag; empty when the closure cannot be read or names a pack that is no longer configured
     */
    static Optional<EffectiveTag> closureTag(StoredActivation stored, AvailablePacks packs) {
        try {
            ClosureSnapshot snapshot = packs.snapshot(stored.document()
                    .packs()
                    .map(list -> list.stream().map(PackRef::packId).toList()));
            return snapshot.resolved()
                    ? Optional.of(EffectiveTag.activation(stored.revision(), snapshot))
                    : Optional.empty();
        } catch (CharterRefusal unreadable) {
            return Optional.empty();
        }
    }
}
