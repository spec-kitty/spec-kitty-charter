package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The one rule for where a companion file may be and how it is held (ADR-0039, #194), shared by the store's read and
 * upsert, the whole-pack store and the source fingerprint, so they cannot disagree on which files a companion may name.
 *
 * <p>A companion path is relative to its artifact's file and resolves, normalized, to a file inside the pack
 * directory. It never ends in {@code .yaml}, so it can never be {@code pack.yaml}, a graph shard or an artifact file.
 * One file has one owner (ADR-0039 decision 3), so writing a companion can never replace another part of the pack: a
 * path that any {@code files} rule, {@code exclusions} rule or {@code payloads} rule of {@code file-kinds.yaml}
 * classifies is refused, naming the rule, except at the companion's own rule, a {@code files} rule whose schema is the
 * companion's declared structure schema in the companion's declared form (a guide classified by its own rule, #194).
 *
 * <p>The content is held in the {@linkplain KindBinding.Companion#form() form the companion declares}: text exactly
 * as read, or Markdown parsed with {@link MarkdownCodec} and validated against the declared structure schema, and
 * written in the canonical form. Markdown never reaches the YAML reader.
 *
 * <p>The containment half of the rule ({@link #inPack}) is also the one every other pack-relative path the store writes
 * passes: an asset file at {@code assets/<path>} and a template at {@code templates/<id>} (#193, ADR-0040), and every
 * part of a whole-pack write. Those are pack-level files, not companions, so they resolve against the pack directory
 * and may be YAML (a sidecar-less {@code *.config.yaml} payload is one).
 */
final class Companions {

    private static final String YAML = ".yaml";

    private Companions() {}

    /**
     * What a companion is held to: the table that classifies pack paths, and the schemas a Markdown companion's
     * structure is validated with. Production uses {@link #bundled()}; a test passes a table and schemas of its own.
     *
     * @param table the {@code file-kinds.yaml} table
     * @param schemas the schemas, looked up only when a Markdown companion is validated
     */
    record Rules(PackFileKind table, Supplier<PackSchemas> schemas) {

        private static final Rules BUNDLED = new Rules(PackFileKind.bundled(), PackSchemas::bundled);

        /**
         * Checks the fields.
         *
         * @param table the table
         * @param schemas the schemas
         */
        Rules {
            Objects.requireNonNull(table, "table");
            Objects.requireNonNull(schemas, "schemas");
        }

        /**
         * The bundled table and schemas.
         *
         * @return the rules every production store uses
         */
        static Rules bundled() {
            return BUNDLED;
        }
    }

    /** Where a declared companion path leads. */
    sealed interface Resolution {}

    /**
     * A companion path that the rule allows.
     *
     * @param file the absolute, normalized file
     * @param packRelative the file relative to the pack directory, {@code /}-separated
     */
    record Inside(Path file, String packRelative) implements Resolution {
        /**
         * Checks the fields.
         *
         * @param file the file
         * @param packRelative the pack-relative path
         */
        Inside {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(packRelative, "packRelative");
        }

        /**
         * The key two companions collide on: the pack-relative path with case ignored, since a case-insensitive file
         * system folds the two into one file.
         *
         * @return the lower-cased pack-relative path
         */
        String claim() {
            return packRelative.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A companion path or file the rule refuses.
     *
     * @param reason why, as a violation names it after the declared path
     */
    record Refused(String reason) implements Resolution, Read {
        /**
         * Checks the reason.
         *
         * @param reason why
         */
        Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The outcome of reading a companion: its content, or why it cannot be read. */
    sealed interface Read {}

    /**
     * A companion read from the pack.
     *
     * @param at where it is
     * @param content what it holds
     */
    record Found(Inside at, CompanionContent content) implements Read {
        /**
         * Checks the fields.
         *
         * @param at where it is
         * @param content what it holds
         */
        Found {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * Resolves a declared companion path against its artifact's file, without touching the file system.
     *
     * @param rules the table and schemas
     * @param packDir the pack directory, absolute and normalized
     * @param artifactFile the artifact's file, relative to the pack directory
     * @param companion the companion as the artifact declares it
     * @return where it leads, or why the rule refuses it
     */
    static Resolution resolve(Rules rules, Path packDir, String artifactFile, KindBinding.Companion companion) {
        Path base = Objects.requireNonNullElse(packDir.resolve(artifactFile).getParent(), packDir);
        Resolution resolution = contained(packDir, base, companion.path());
        if (!(resolution instanceof Inside at)) {
            return resolution;
        }
        // The resolved name, not the declared text: "pack.yaml/" and "x.tactic.yaml/." both normalize to a YAML file.
        if (String.valueOf(at.file().getFileName()).toLowerCase(Locale.ROOT).endsWith(YAML)) {
            return new Refused("is a YAML file");
        }
        return claimedByRule(rules.table(), packDir.getFileName() + "/" + at.packRelative(), companion)
                .<Resolution>map(Refused::new)
                .orElse(at);
    }

    /**
     * Why a rule of the table owns the path rather than the companion, or empty when no rule does or the rule is the
     * companion's own. A claimed file is a pack inconsistency, refused rather than resolved by precedence.
     */
    private static Optional<String> claimedByRule(PackFileKind table, String path, KindBinding.Companion companion) {
        return switch (table.classify(path)) {
            case PackFileKind.Unclassified unclassified -> Optional.empty();
            case PackFileKind.Excluded excluded ->
                Optional.of("is excluded by file-kinds rule '" + excluded.exclusionClass() + "', not a companion");
            case PackFileKind.Mapped mapped
            when mapped.schema().isEmpty() ->
                // A pack-level file (#193, ADR-0040): an asset file or a template.
                Optional.of("is a pack file of kind " + mapped.kind() + ", not a companion");
            case PackFileKind.Mapped mapped -> {
                boolean own = companion.structureSchema().equals(Optional.of(mapped.kind()))
                        && companion.form() == mapped.contentForm();
                yield own
                        ? Optional.empty()
                        : Optional.of("is a pack file of kind " + mapped.kind() + " (file-kinds rule '"
                                + String.join("', '", table.matchingRules(path)) + "'), not a companion");
            }
        };
    }

    /**
     * Resolves a pack-relative path, without touching the file system: the containment half of the companion rule, for
     * a file the pack holds in its own right (an asset file, a template, any part of a whole-pack write).
     *
     * @param packDir the pack directory, absolute and normalized
     * @param packRelative the path relative to the pack directory, {@code /}-separated
     * @return where it leads, or why the rule refuses it
     */
    static Resolution inPack(Path packDir, String packRelative) {
        return contained(packDir, packDir, packRelative);
    }

    /** A declared path is not empty, not absolute, has no backslash, and resolves against {@code base} into the pack. */
    private static Resolution contained(Path packDir, Path base, String declared) {
        if (declared.isBlank()) {
            return new Refused("is empty");
        }
        // A backslash is a separator on one file system and a name character on another; refused, a pack reads alike
        // everywhere.
        if (declared.indexOf('\\') >= 0) {
            return new Refused("is not a valid path");
        }
        Path relative;
        try {
            relative = Path.of(declared);
        } catch (InvalidPathException invalid) {
            return new Refused("is not a valid path");
        }
        if (relative.isAbsolute() || declared.startsWith("/")) {
            return new Refused("leaves the pack");
        }
        Path file = base.resolve(relative).normalize();
        if (!file.startsWith(packDir) || file.equals(packDir)) {
            return new Refused("leaves the pack");
        }
        return new Inside(
                file,
                packDir.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"));
    }

    /**
     * Resolves and reads a companion's text, whatever its form: it must be a regular file, not reached through a link,
     * whose real location is in the pack, holding UTF-8 text. The source fingerprint digests exactly the companions
     * this finds, so a change to a Markdown companion moves the fingerprint even while its structure is refused.
     *
     * @param rules the table and schemas
     * @param packDir the pack directory, absolute and normalized
     * @param artifactFile the artifact's file, relative to the pack directory
     * @param companion the companion as the artifact declares it
     * @return the text, as {@link CompanionContent.Text}, or why it cannot be read
     */
    static Read locate(Rules rules, Path packDir, String artifactFile, KindBinding.Companion companion) {
        Resolution resolution = resolve(rules, packDir, artifactFile, companion);
        if (!(resolution instanceof Inside at)) {
            return (Refused) resolution;
        }
        if (!Files.exists(at.file(), LinkOption.NOFOLLOW_LINKS)) {
            return new Refused("missing");
        }
        if (!Files.isRegularFile(at.file(), LinkOption.NOFOLLOW_LINKS)) {
            return new Refused("is not a regular file");
        }
        if (!FilesystemPackStore.realPathContained(packDir, at.file())) {
            return new Refused("leaves the pack");
        }
        Optional<String> text = FilesystemPackStore.readText(at.file());
        if (text.isEmpty()) {
            return new Refused("cannot be read as UTF-8 text");
        }
        return new Found(at, new CompanionContent.Text(text.get()));
    }

    /**
     * Resolves and reads a companion in its declared form: {@link #locate} the text, then, for Markdown, parse it and
     * validate the model against the declared structure schema. A Markdown refusal names its line; a structure breach
     * names each failed keyword and where.
     *
     * @param rules the table and schemas
     * @param packDir the pack directory, absolute and normalized
     * @param artifactFile the artifact's file, relative to the pack directory
     * @param companion the companion as the artifact declares it
     * @return the content in the declared form, or why it cannot be read
     */
    static Read read(Rules rules, Path packDir, String artifactFile, KindBinding.Companion companion) {
        Read located = locate(rules, packDir, artifactFile, companion);
        if (!(located instanceof Found found)) {
            return located;
        }
        String text = Companions.text(found.content());
        return switch (companion.form()) {
            case TEXT -> found;
            case MARKDOWN ->
                switch (MarkdownCodec.parse(text)) {
                    case MarkdownRefusal refusal -> new Refused(refusal.message());
                    case MarkdownCodec.Parsed parsed -> {
                        CompanionContent content = new CompanionContent.Markdown(parsed.document());
                        yield breach(rules, companion, found.at().packRelative(), content)
                                .<Read>map(Refused::new)
                                .orElse(new Found(found.at(), content));
                    }
                };
            case YAML, BYTES ->
                throw new IllegalStateException(
                        "a companion is never " + companion.form().token());
        };
    }

    /**
     * Why a companion's content breaks the structure its companion declares, or empty: text has no structure; a
     * Markdown model is validated against the declared structure schema.
     *
     * @param rules the table and schemas
     * @param companion the companion, with its structure schema
     * @param packRelative where the companion is, named in the schema violations
     * @param content the content
     * @return the breach, each violation as {@code structure: <keyword> violated at <where>}, joined by {@code ; }
     * @throws IllegalArgumentException when the declared structure schema is not loaded: a binding's error
     */
    static Optional<String> breach(
            Rules rules, KindBinding.Companion companion, String packRelative, CompanionContent content) {
        return switch (content) {
            case CompanionContent.Text text -> Optional.empty();
            case CompanionContent.Markdown markdown -> {
                List<SchemaViolation> violations = rules.schemas()
                        .get()
                        .validateMarkdown(companion.structureSchema().orElseThrow(), packRelative, markdown.document());
                yield violations.isEmpty()
                        ? Optional.empty()
                        : Optional.of(violations.stream()
                                .map(violation -> "structure: " + FilesystemPackStore.schemaReason(violation))
                                .collect(Collectors.joining("; ")));
            }
        };
    }

    /**
     * The text a companion's content is written as: text as it is, Markdown in its canonical form.
     *
     * @param content the content
     * @return its text
     * @throws IllegalStateException when a Markdown model has no canonical form that reads back as itself
     */
    static String text(CompanionContent content) {
        return switch (content) {
            case CompanionContent.Text text -> text.value();
            case CompanionContent.Markdown markdown -> MarkdownCodec.write(markdown.document());
        };
    }
}
