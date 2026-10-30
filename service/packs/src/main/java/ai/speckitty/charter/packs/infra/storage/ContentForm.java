package ai.speckitty.charter.packs.infra.storage;

/**
 * How a pack file is read, held, written and compared, as its {@code file-kinds.yaml} rule declares it with
 * {@code content_form}. A file's extension and an asset's {@code mime} never choose it.
 *
 * <p>Every form is named here; the table accepts only the forms the store builds (see
 * {@link PackFileKind}), so a form that is not built yet is refused when the table loads, never silently treated.
 */
enum ContentForm {
    /** A YAML document, compared by meaning; the default of a {@code files} rule. */
    YAML("yaml"),
    /**
     * A Markdown document, read into a {@link ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument} and
     * compared by that model (#194, ADR-0040 as amended): a {@code files} rule may declare it, and its schema then
     * validates the parsed model ({@link PackSchemas#validateMarkdown}). The store reads it as a bound kind's
     * companion (a toolguide's guide, #194) or as the pack document ({@code pack.md}, #260); any other file of it fails
     * loudly when a pack is listed or validated, until the rule that adds it wires its own store path.
     */
    MARKDOWN("markdown"),
    /**
     * Plain text. Not built for {@code files} rules, so the table refuses it there; the mission that first needs a
     * text {@code files} rule builds and admits it. A companion declares it (ADR-0039, #194): the text companion,
     * read and written byte for byte ({@link KindBinding.Companion}).
     */
    TEXT("text"),
    /** Opaque bytes, written and compared byte for byte. */
    BYTES("bytes");

    private final String token;

    ContentForm(String token) {
        this.token = token;
    }

    /** The token {@code file-kinds.yaml} writes for this form. */
    String token() {
        return token;
    }

    /**
     * The form a table token names.
     *
     * @param token the {@code content_form} value
     * @return the form
     * @throws IllegalStateException when no form has that token, naming the token and the table
     */
    static ContentForm fromToken(String token) {
        for (ContentForm form : values()) {
            if (form.token.equals(token)) {
                return form;
            }
        }
        throw new IllegalStateException(PackFileKind.RESOURCE + ": unknown content_form '" + token + "'");
    }
}
