package ai.speckitty.charter.packs.domain;

import java.util.List;

/**
 * Where a {@link GlossaryContext} applies (ADR-0049 decision 14): advisory, so nothing filters by it. Each list keeps
 * its authored order; an empty list stands for an absent one, as ADR-0036 decision 2 reads them alike.
 *
 * @param paths path globs
 * @param languages language tokens
 * @param stacks stack tokens
 */
public record AppliesTo(List<String> paths, List<String> languages, List<String> stacks) {

    /** Applies nowhere in particular: every list empty, as when the file states no {@code applies_to}. */
    public static final AppliesTo NOWHERE_IN_PARTICULAR = new AppliesTo(List.of(), List.of(), List.of());

    /**
     * Copies the lists.
     *
     * @param paths the path globs
     * @param languages the language tokens
     * @param stacks the stack tokens
     */
    public AppliesTo {
        paths = List.copyOf(paths);
        languages = List.copyOf(languages);
        stacks = List.copyOf(stacks);
    }

    /**
     * Whether the context states nothing about where it applies.
     *
     * @return {@code true} when every list is empty
     */
    public boolean isEmpty() {
        return paths.isEmpty() && languages.isEmpty() && stacks.isEmpty();
    }
}
