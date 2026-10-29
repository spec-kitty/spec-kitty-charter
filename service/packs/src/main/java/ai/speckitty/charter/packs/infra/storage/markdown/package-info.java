/**
 * ADR-0040's {@code markdown} content form: a Markdown document read into a structural model
 * ({@link ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument}: optional frontmatter, optional title,
 * preamble and H2 sections), written back canonically by
 * {@link ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec}, and compared by that model (equal models are
 * equal in meaning). Ambiguous Markdown is refused with a
 * {@link ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal} that names the line. The scanner is a line
 * scanner that follows CommonMark's fence rules, not a full CommonMark parser, and no third-party parser is used.
 */
@NullMarked
package ai.speckitty.charter.packs.infra.storage.markdown;

import org.jspecify.annotations.NullMarked;
