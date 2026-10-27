package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The one rule for where a companion file may be (ADR-0039), shared by the store's read and upsert and by the source
 * fingerprint, so the three cannot disagree on which files a companion may name.
 *
 * <p>A companion path is relative to its artifact's file and resolves, normalized, to a file inside the pack
 * directory. It never ends in {@code .yaml}, so it can never be {@code pack.yaml}, a graph shard or an artifact file:
 * writing a companion can never replace another part of the pack.
 *
 * <p>The containment half of the rule ({@link #inPack}) is also the one every other pack-relative path the store writes
 * passes: an asset file at {@code assets/<path>} and a template at {@code templates/<id>} (#193, ADR-0040), and every
 * part of a whole-pack write. Those are pack-level files, not companions, so they resolve against the pack directory
 * and may be YAML (a sidecar-less {@code *.config.yaml} payload is one).
 */
final class Companions {

    private static final String YAML = ".yaml";

    private Companions() {}

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
     * @param packDir the pack directory, absolute and normalized
     * @param artifactFile the artifact's file, relative to the pack directory
     * @param declared the companion path as the artifact declares it
     * @return where it leads, or why the rule refuses it
     */
    static Resolution resolve(Path packDir, String artifactFile, String declared) {
        Path base = Objects.requireNonNullElse(packDir.resolve(artifactFile).getParent(), packDir);
        Resolution resolution = contained(packDir, base, declared);
        // The resolved name, not the declared text: "pack.yaml/" and "x.tactic.yaml/." both normalize to a YAML file.
        if (resolution instanceof Inside at
                && String.valueOf(at.file().getFileName())
                        .toLowerCase(Locale.ROOT)
                        .endsWith(YAML)) {
            return new Refused("is a YAML file");
        }
        return resolution;
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
     * Resolves and reads a companion: it must be a regular file, not reached through a link, whose real location is in
     * the pack, holding UTF-8 text.
     *
     * @param packDir the pack directory, absolute and normalized
     * @param artifactFile the artifact's file, relative to the pack directory
     * @param declared the companion path as the artifact declares it
     * @return the content, or why it cannot be read
     */
    static Read read(Path packDir, String artifactFile, String declared) {
        Resolution resolution = resolve(packDir, artifactFile, declared);
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
     * The text a companion's content is written as.
     *
     * @param content the content
     * @return its text
     */
    static String text(CompanionContent content) {
        return switch (content) {
            case CompanionContent.Text text -> text.value();
        };
    }
}
