package ai.speckitty.charter.profiles.application;

import java.util.Objects;
import java.util.Optional;

/**
 * The words of an edge: the condition and the reason it states. Either may be absent, since an edge may state one, both
 * or neither (FR-004). A governance entry carries the words of the edge the walk reached it through, whatever the
 * relation of that edge ({@code specializes_from}, {@code requires}, {@code suggests}, {@code vocabulary}); an edge that
 * states neither word is {@link #NONE}, which the wire writes as an empty object.
 *
 * <p>Where more than one edge could reach an entry, the words are those of the edge of the parent the walk keeps: the
 * first reach, by the fewest hops, then the smallest parent, then the smallest edge. The walk lists an artifact once, at
 * the earliest step that reaches it, so a later step or another edge does not change them.
 *
 * @param when the condition under which the edge applies
 * @param reason why the edge is there
 */
public record EdgeWords(Optional<String> when, Optional<String> reason) {

    /** The edge that states neither word. */
    public static final EdgeWords NONE = new EdgeWords(Optional.empty(), Optional.empty());

    /**
     * Checks the fields.
     *
     * @param when the condition
     * @param reason the reason
     */
    public EdgeWords {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
    }
}
