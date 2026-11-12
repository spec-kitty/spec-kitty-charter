package ai.speckitty.charter.packs.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A pack's document as the document read serves it (#451): the held {@code README.md} text for the {@code text/markdown}
 * form, and its parts for the {@code application/json} form. A read model, not an aggregate part: the aggregate's own
 * document is {@link PackDocument}.
 *
 * @param markdown the held file's text, byte for byte as decoded, frontmatter included
 * @param title the document's H1, when it has one
 * @param body the Markdown below the H1 in canonical form: the preamble and the sections, without the frontmatter and
 *     the title; empty when the document holds nothing else
 */
public record PackDocumentText(String markdown, Optional<String> title, String body) {

    /**
     * Checks the fields.
     *
     * @param markdown the held text
     * @param title the H1, if any
     * @param body the canonical body
     */
    public PackDocumentText {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }
}
