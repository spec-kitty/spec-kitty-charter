package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What {@link StepEnhancementRules} judges: the facts of one pack's step enhancements, their companions and the pack's
 * graph edges, as plain values. The caller (validate mode, load) fills them; no store, inspector or refusal type
 * appears here, so the rules stay pure.
 *
 * <p>A companion's {@linkplain Companion#canonicalText() canonical text} is the caller's {@code
 * MarkdownCodec.write(document)} of the parsed companion (A5): the rules never parse Markdown themselves, and load,
 * validate mode and the read agree on one text. A companion the caller's own read refused is marked
 * {@linkplain Companion#unreadable(String) unreadable}; that read already reported it, so no rule judges it again.
 *
 * <p>All paths are pack-relative and {@code /}-separated. The orphan set is filled in validate mode only: at load the
 * caller passes none, because the store lists no unnamed file, so {@link StepEnhancementFault#COMPANION_ORPHAN} never
 * fires there.
 *
 * @param enhancements the step enhancements that were parsed into the facts
 * @param companions the companions that exist beside them, each read once
 * @param fragmentFiles every {@code *.prompt-fragment.md} found under {@code missions/*}{@code /enhancements/}; the
 *     orphan candidates
 * @param unparsedEnhancementFiles the {@code *.step-enhancement.yaml} files the caller could not parse into the facts
 *     or withheld (A4): a fragment in their directory is never judged an orphan, because the file's own fault already
 *     refuses the pack and the fragment may be one it names
 * @param edges the pack's {@code enhances} and {@code overrides} edges whose source is a {@code step_enhancement:}
 *     node
 */
public record StepEnhancementFacts(
        List<Enhancement> enhancements,
        List<Companion> companions,
        Set<String> fragmentFiles,
        Set<String> unparsedEnhancementFiles,
        List<Edge> edges) {

    /** The URN prefix of a step enhancement node. */
    static final String URN_PREFIX = NodeKind.STEP_ENHANCEMENT.token() + ":";

    /**
     * Copies the collections.
     *
     * @param enhancements the parsed enhancements
     * @param companions the companions read
     * @param fragmentFiles the fragment files found
     * @param unparsedEnhancementFiles the enhancement files not parsed or withheld
     * @param edges the step enhancement edges
     */
    public StepEnhancementFacts {
        enhancements = List.copyOf(enhancements);
        companions = List.copyOf(companions);
        fragmentFiles = Set.copyOf(fragmentFiles);
        unparsedEnhancementFiles = Set.copyOf(unparsedEnhancementFiles);
        edges = List.copyOf(edges);
    }

    /**
     * A pack with no step enhancement fact at all.
     *
     * @return empty facts
     */
    public static StepEnhancementFacts none() {
        return new StepEnhancementFacts(List.of(), List.of(), Set.of(), Set.of(), List.of());
    }

    /**
     * One parsed step enhancement.
     *
     * @param path the pack-relative path of its {@code *.step-enhancement.yaml}
     * @param typeDirectory the {@code <type>} of the {@code missions/<type>/enhancements/} directory it sits in
     * @param id its id, equal to its file stem (the store's stem rule refuses any other id first)
     * @param entries its {@code prompt_fragments} entries, in authored order; none when the key is absent
     */
    public record Enhancement(String path, String typeDirectory, String id, List<Entry> entries) {
        /**
         * Copies the entries.
         *
         * @param path the file path
         * @param typeDirectory the type directory
         * @param id the id
         * @param entries the entries
         */
        public Enhancement {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(typeDirectory, "typeDirectory");
            Objects.requireNonNull(id, "id");
            entries = List.copyOf(entries);
        }

        /**
         * The directory the file sits in, with a trailing slash.
         *
         * @return the directory part of {@link #path()}
         */
        public String directory() {
            return path.substring(0, path.lastIndexOf('/') + 1);
        }

        /**
         * The pack-relative path of the companion an entry names, beside this file.
         *
         * @param entry the entry
         * @return the companion's path
         */
        public String companionPath(Entry entry) {
            return directory() + entry.companion();
        }
    }

    /**
     * One {@code prompt_fragments} entry.
     *
     * @param slot the slot id as written (its grammar is the schema's, not a rule's)
     * @param companion the companion's name, beside the enhancement file
     */
    public record Entry(String slot, String companion) {
        /**
         * Checks the parts.
         *
         * @param slot the slot id
         * @param companion the companion name
         */
        public Entry {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(companion, "companion");
        }
    }

    /**
     * One companion that exists, as the caller's read found it.
     *
     * @param path its pack-relative path
     * @param unreadable whether the caller's read refused it (already reported there)
     * @param hasFrontmatter whether it carries frontmatter
     * @param blank whether its body is blank
     * @param canonicalText {@code MarkdownCodec.write(document)} of the parsed companion; empty when unreadable
     */
    public record Companion(
            String path, boolean unreadable, boolean hasFrontmatter, boolean blank, String canonicalText) {
        /**
         * Checks the parts.
         *
         * @param path the path
         * @param unreadable whether the read refused it
         * @param hasFrontmatter whether it has frontmatter
         * @param blank whether it is blank
         * @param canonicalText the canonical text
         */
        public Companion {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(canonicalText, "canonicalText");
        }

        /**
         * A companion that was read.
         *
         * @param path its path
         * @param hasFrontmatter whether it carries frontmatter
         * @param blank whether its body is blank
         * @param canonicalText its canonical text
         * @return the fact
         */
        public static Companion read(String path, boolean hasFrontmatter, boolean blank, String canonicalText) {
            return new Companion(path, false, hasFrontmatter, blank, canonicalText);
        }

        /**
         * A companion the caller's read refused.
         *
         * @param path its path
         * @return the fact
         */
        public static Companion unreadable(String path) {
            return new Companion(path, true, false, false, "");
        }
    }

    /**
     * One graph edge whose source is a {@code step_enhancement:} node.
     *
     * @param relation {@code ENHANCES} or {@code OVERRIDES}; any other relation is ignored
     * @param source the source URN, {@code step_enhancement:<id>}
     * @param target the target URN without its pack qualifier, for example {@code action:software-dev/plan}; split a
     *     written {@code <pack>::<urn>} with {@code PackQualifiedReference.parse} first; a target still holding
     *     {@code ::} is refused with an {@link IllegalArgumentException}
     * @param qualifier the {@code <pack>::} qualifier the target was written with; empty for a bare target
     */
    public record Edge(RelationType relation, String source, String target, Optional<String> qualifier) {
        /**
         * Checks the parts.
         *
         * @param relation the relation
         * @param source the source URN
         * @param target the target URN, without a {@code ::}
         * @param qualifier the qualifier
         * @throws IllegalArgumentException when the target still carries its pack qualifier
         */
        public Edge {
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(qualifier, "qualifier");
            if (target.contains("::")) {
                throw new IllegalArgumentException(
                        "The edge target must not carry its pack qualifier (split it into the qualifier), but is '"
                                + target + "'");
            }
        }
    }
}
