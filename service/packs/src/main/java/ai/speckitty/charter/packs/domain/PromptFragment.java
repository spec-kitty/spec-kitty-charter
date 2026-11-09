package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.Objects;

/**
 * The Markdown body of one {@code *.prompt-fragment.md} companion of a {@link StepEnhancement} (ADR-0066): where the
 * file sits beside the enhancement and what it says. Its meaning is its canonical render, so a fragment is equal to
 * another when both hold the same parsed document (ADR-0036, ADR-0040). The slot it fills is the enhancement entry's,
 * not the fragment's.
 *
 * @param path the companion's name, beside the enhancement file
 * @param body the parsed Markdown document
 */
public record PromptFragment(String path, MarkdownDocument body) {

    /**
     * Checks that both components are present.
     *
     * @param path the companion's name
     * @param body the parsed document
     */
    public PromptFragment {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(body, "body");
    }
}
