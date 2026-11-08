package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What an activation document asks of the in-force resolution (FR-013 to FR-016): its selection, its deny entries, its
 * language filter and its version pins, as plain values. The resolution lives in {@code packs}, since each of its strata
 * walks edges (C-002), and the {@code activations} slice reaches it only through the port, so nothing here names a
 * document type.
 *
 * @param selected the ids the document selects, of every kind, mission types included (one kind model, IC-20)
 * @param deactivated the ids the document denies, of every kind
 * @param languages the document's language filter; empty when the document sets none (rule E2: no filter), which is not
 *     the same as a filter that names no language
 * @param pins the version pins of the document, by configured pack id. The resolution carries them and never consults
 *     them: a pin decides which packs compose, upstream of the merged closure, and the {@code pinNotHeld} report is the
 *     adapter's (B1)
 */
public record InForceRequest(
        Set<Urn> selected, Set<Urn> deactivated, Optional<Set<String>> languages, Map<String, String> pins) {

    /** A request that selects nothing, denies nothing, filters no language and pins no pack. */
    public static final InForceRequest NOTHING = new InForceRequest(Set.of(), Set.of(), Optional.empty(), Map.of());

    /**
     * Checks and copies the fields.
     *
     * @param selected the selected ids
     * @param deactivated the denied ids
     * @param languages the language filter, if the document sets one
     * @param pins the version pins by configured pack id
     */
    public InForceRequest {
        selected = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(selected, "selected")));
        deactivated =
                Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(deactivated, "deactivated")));
        languages = Objects.requireNonNull(languages, "languages")
                .map(set -> Collections.unmodifiableSet(new LinkedHashSet<>(set)));
        pins = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(pins, "pins")));
    }

    /**
     * The request with a selection.
     *
     * @param urns the ids to select
     * @return a request that selects them and asks nothing else
     */
    public static InForceRequest selecting(Set<Urn> urns) {
        return new InForceRequest(urns, Set.of(), Optional.empty(), Map.of());
    }

    /**
     * Whether an artifact passes the language filter (rule E2): there is no filter, or the artifact declares no
     * language, or one of its languages is in the filter.
     *
     * @param declared the languages the artifact declares; empty when it declares none
     * @return {@code true} when the artifact is within the filter
     */
    public boolean admits(Set<String> declared) {
        Objects.requireNonNull(declared, "declared");
        return languages
                .map(filter -> declared.isEmpty() || !Collections.disjoint(filter, declared))
                .orElse(true);
    }
}
