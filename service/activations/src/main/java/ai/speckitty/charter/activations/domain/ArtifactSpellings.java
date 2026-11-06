package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The known spellings of an artifact id (ADR-0020 D4, operator ruling of 2026-11-06), as one function every write
 * judges an id by: the activation document's {@code selected} and {@code deactivated} lists, and the per-artifact
 * writes (select, unselect, deactivate, reactivate and preset) that take an id from a path or a body.
 *
 * <p>An id is spelled as the declared id, as the full URN of the list's kind ({@code directive:DIRECTIVE_001}), or as
 * a case variant of either. The declared id is always what is stored. A spelling matching two declared ids that differ
 * only by case is ambiguous, unless it is exactly one of them. A URN of another kind than the list's is unknown.
 */
public final class ArtifactSpellings {

    private ArtifactSpellings() {}

    /** What an id spelled some way means in a closure. */
    public sealed interface Resolution {

        /**
         * The spelling names one declared id.
         *
         * @param id the declared id, which is stored
         */
        record Declared(String id) implements Resolution {}

        /** The closure holds no artifact of the list's kind this spelling names. */
        record Unknown() implements Resolution {}

        /**
         * The spelling matches several declared ids that differ only by case.
         *
         * @param candidates the declared ids, in code point order
         */
        record Ambiguous(List<String> candidates) implements Resolution {

            /** Copies the candidates. */
            public Ambiguous {
                candidates = List.copyOf(candidates);
            }
        }
    }

    /**
     * Resolves a spelled id.
     *
     * @param kind the kind of the list the id is written in
     * @param spelled the id as written
     * @param held the declared ids of that kind the closure holds
     * @return the declared id, {@link Resolution.Unknown} or {@link Resolution.Ambiguous}
     */
    public static Resolution resolve(ArtifactKind kind, String spelled, Set<String> held) {
        String id = spelled;
        if (spelled.indexOf(':') > 0) {
            var urn = Urn.parse(spelled)
                    .filter(parsed ->
                            parsed.kind().artifactKind().filter(kind::equals).isPresent());
            if (urn.isEmpty()) {
                return new Resolution.Unknown();
            }
            id = urn.orElseThrow().id();
        }
        if (held.contains(id)) {
            return new Resolution.Declared(id);
        }
        String folded = id.toLowerCase(Locale.ROOT);
        List<String> matches = held.stream()
                .filter(declared -> declared.toLowerCase(Locale.ROOT).equals(folded))
                .sorted(ActivationDocument.CODE_POINT_ORDER)
                .toList();
        return switch (matches.size()) {
            case 0 -> new Resolution.Unknown();
            case 1 -> new Resolution.Declared(matches.getFirst());
            default -> new Resolution.Ambiguous(matches);
        };
    }
}
