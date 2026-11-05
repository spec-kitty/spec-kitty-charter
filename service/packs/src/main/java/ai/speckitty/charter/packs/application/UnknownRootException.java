package ai.speckitty.charter.packs.application;

import java.io.Serial;
import java.util.List;

/**
 * A closure read rooted at ids of which some name no configured pack (research R4): the one fault of a rooted read
 * that the caller, not the packs, answers for. {@link ClosureQueries#snapshot} checks the roots against the catalog
 * before it composes, inside the same verified read, and names every unknown root, so a caller tells this case apart
 * from any other {@link IllegalArgumentException} of the compose by its type alone.
 */
public final class UnknownRootException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The ids no pack is configured as, in the order the roots named them, each once. */
    private final transient List<String> unknownIds;

    /**
     * The roots no pack is configured as.
     *
     * @param unknownIds the configured ids that name no pack, in the roots' order, each once; not empty
     * @throws IllegalArgumentException when no id is given
     */
    public UnknownRootException(List<String> unknownIds) {
        super("No pack is configured as " + String.join(", ", unknownIds) + ".");
        if (unknownIds.isEmpty()) {
            throw new IllegalArgumentException("An unknown root names at least one id.");
        }
        this.unknownIds = List.copyOf(unknownIds);
    }

    /**
     * The ids no pack is configured as.
     *
     * @return the ids, in the roots' order, each once
     */
    public List<String> unknownIds() {
        return unknownIds;
    }
}
