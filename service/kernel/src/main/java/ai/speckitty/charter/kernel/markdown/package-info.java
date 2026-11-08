/**
 * The line rules of ADR-0040's {@code markdown} content form, in the kernel so that every domain holding Markdown text
 * applies one rule (#299): the scanner ({@link ai.speckitty.charter.kernel.markdown.MarkdownScanner}) and the rules a
 * heading's text, a block and a document's opening follow ({@link ai.speckitty.charter.kernel.markdown.MarkdownLines}).
 * The packs domain's structural model ({@code packs.domain.markdown.MarkdownDocument}) and the missions domain's step
 * texts ({@code missions.domain.StepDocument}) check their content through {@code MarkdownLines} when they are built;
 * the scanner's {@code scan}, {@code Line}, {@code Kind}, {@code Refused} and {@code refused} are public for the packs
 * codec ({@code infra.storage.markdown}), which reads whole texts with them. Depends on {@code java} and JSpecify only.
 */
@NullMarked
package ai.speckitty.charter.kernel.markdown;

import org.jspecify.annotations.NullMarked;
