/**
 * ADR-0040's {@code markdown} content form, as stored: a Markdown text read into the domain's structural model
 * ({@link ai.speckitty.charter.packs.domain.markdown.MarkdownDocument}) and written back canonically by
 * {@link ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec}. Ambiguous Markdown is refused with a
 * {@link ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal} that names the line. The line rules are the
 * domain's ({@link ai.speckitty.charter.packs.domain.markdown.MarkdownScanner}): a line scanner that follows
 * CommonMark's fence rules, not a full CommonMark parser, and no third-party parser is used.
 */
@NullMarked
package ai.speckitty.charter.packs.infra.storage.markdown;

import org.jspecify.annotations.NullMarked;
