/**
 * The model of ADR-0040's {@code markdown} content form and its content rules, in one home (#271): a Markdown document
 * as a structural model ({@link ai.speckitty.charter.packs.domain.markdown.MarkdownDocument}: optional frontmatter,
 * optional title, preamble and H2 sections) and the line rules that decide what text can stand in one
 * ({@link ai.speckitty.charter.packs.domain.markdown.MarkdownScanner}). The codec that reads and writes the text, and
 * loads the frontmatter's YAML, is infrastructure ({@code infra.storage.markdown}); the domain values that hold Markdown
 * check their content through these rules when they are built.
 */
@NullMarked
package ai.speckitty.charter.packs.domain.markdown;

import org.jspecify.annotations.NullMarked;
