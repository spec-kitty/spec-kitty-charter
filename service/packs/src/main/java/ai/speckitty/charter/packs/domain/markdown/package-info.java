/**
 * The model of ADR-0040's {@code markdown} content form (#271): a Markdown document as a structural model
 * ({@link ai.speckitty.charter.packs.domain.markdown.MarkdownDocument}: optional frontmatter, optional title, preamble
 * and H2 sections). The line rules that decide what text can stand in one live in the kernel
 * ({@code kernel.markdown}), so that the missions domain's step texts apply them too (#299); the model checks its
 * content through them when it is built. The codec that reads and writes the text, and loads the frontmatter's YAML,
 * is infrastructure ({@code infra.storage.markdown}).
 */
@NullMarked
package ai.speckitty.charter.packs.domain.markdown;

import org.jspecify.annotations.NullMarked;
