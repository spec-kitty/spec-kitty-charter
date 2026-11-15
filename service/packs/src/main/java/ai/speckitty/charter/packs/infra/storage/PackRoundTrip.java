package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The whole-pack round trip, the production flow's public entry (ADR-0036; ADR-0075 decision 8, as amended by operator
 * ruling 2 of 2026-11-15): read a pack directory into the domain, write it into an empty directory through the storage
 * service ({@code DomainRepositoryService}), read the written pack back, and read both sides of every modelled file in
 * its content form, with the pack readers' own settings.
 *
 * <p>It judges nothing. The verdict, equality of meaning against an expected baseline, is {@code pack-validation}'s,
 * over the {@link PackRoundTripResult} this returns. A composition root calls it: the whole-pack gate and the drift test,
 * and later {@code pack-check --round-trip} (#478).
 */
public final class PackRoundTrip {

    private PackRoundTrip() {}

    /**
     * Round-trips one pack through the storage service.
     *
     * @param source the pack directory to read; never written
     * @param target an empty or missing directory that becomes the written pack
     * @return what the round trip observed
     */
    public static PackRoundTripResult run(Path source, Path target) {
        return run(new DomainRoundTrip(new YamlSerializationStrategy()), source, target);
    }

    /**
     * Every file of a pack with its gate kind, as {@link #run} counts them, without round-tripping it.
     *
     * @param pack the pack directory
     * @return pack-relative path ({@code /}-separated) to gate kind, sorted by path
     */
    public static SortedMap<String, String> census(Path pack) {
        return WholePackCensus.of(pack);
    }

    /**
     * The gate kinds the round trip models, as the census names them.
     *
     * @return the modelled gate kinds
     */
    public static Set<String> modelledKinds() {
        return DomainRoundTrip.MODELLED;
    }

    /**
     * The schema names of the bundled {@code file-kinds.yaml} table's {@code files} rules: what the round trip must model
     * or a baseline must list with a reason (#547).
     *
     * @return the schema names, sorted
     */
    public static SortedSet<String> fileKindsSchemas() {
        return PackFileKind.bundled().fileRules().stream()
                .map(PackFileKind.FileRule::schema)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Round-trips one pack through {@code roundTrip}, with the pack's own census and content forms.
     *
     * @param roundTrip the adapter; a control passes a lossy one
     * @param source the pack directory
     * @param target the directory to write to
     * @return what the round trip observed
     */
    static PackRoundTripResult run(WholePackRoundTrip roundTrip, Path source, Path target) {
        return run(roundTrip, source, target, WholePackCensus.of(source), WholePackCensus.formsOf(source));
    }

    /**
     * Round-trips one pack and reads both sides of every answered file by the content form {@code formOf} gives its
     * path. The form is decided before any text is read, and the dispatch is exhaustive, so a new form is decided here.
     *
     * @param roundTrip the adapter
     * @param source the pack directory
     * @param target the directory to write to
     * @param census every source file's gate kind
     * @param formOf the content form of a source path
     * @return what the round trip observed
     */
    static PackRoundTripResult run(
            WholePackRoundTrip roundTrip,
            Path source,
            Path target,
            SortedMap<String, String> census,
            Function<String, ContentForm> formOf) {
        Map<String, Outcome> outcomes = roundTrip.roundTrip(source, target, census);
        SortedMap<String, FileOutcome> read = new TreeMap<>();
        outcomes.forEach((path, outcome) -> read.put(
                path,
                switch (outcome) {
                    case WholePackRoundTrip.Refused r -> new FileOutcome.Refused(r.reason());
                    case WholePackRoundTrip.Written w ->
                        written(formOf.apply(path), census.getOrDefault(path, ""), source, path, target, w, outcomes);
                }));
        return new PackRoundTripResult(census, roundTrip.modelledKinds(), read, writtenFiles(target));
    }

    private static FileOutcome written(
            ContentForm form,
            String kind,
            Path source,
            String path,
            Path target,
            WholePackRoundTrip.Written written,
            Map<String, Outcome> outcomes) {
        Path sourceFile = source.resolve(path);
        Path writtenFile = target.resolve(written.writtenPath());
        return switch (form) {
            // #193: a payload need not be text.
            case BYTES ->
                new FileOutcome.Written(
                        written.writtenPath(),
                        new Reading.Bytes(bytes(sourceFile)),
                        new Reading.Bytes(bytes(writtenFile)));
            // #194: read by the Markdown model; the text never reaches the YAML reader.
            case MARKDOWN ->
                new FileOutcome.Written(written.writtenPath(), markdown(text(sourceFile)), markdown(text(writtenFile)));
            // ADR-0039: a text companion, compared exactly.
            case TEXT ->
                new FileOutcome.Written(
                        written.writtenPath(), new Reading.Text(text(sourceFile)), new Reading.Text(text(writtenFile)));
            case YAML ->
                yaml(kind, source, target, written.writtenPath(), text(sourceFile), text(writtenFile), outcomes);
        };
    }

    /**
     * A YAML file, parsed as the readers parse it. For the manifest (#195), its derived fields, which the comparison
     * skips (ADR-0042), are verified on both sides: the source against the source bytes, the written manifest against the
     * written file each listed path paired with.
     */
    private static FileOutcome yaml(
            String kind,
            Path source,
            Path target,
            String writtenPath,
            String sourceText,
            String writtenText,
            Map<String, Outcome> outcomes) {
        Reading.Yaml sourceTree = new Reading.Yaml(Yaml.load(sourceText));
        Reading.Yaml writtenTree = new Reading.Yaml(Yaml.load(writtenText));
        if (!DomainRoundTrip.MANIFEST.equals(kind)) {
            return new FileOutcome.Written(writtenPath, sourceTree, writtenTree);
        }
        YamlSerializationStrategy yaml = new YamlSerializationStrategy();
        List<String> sourceStale =
                yaml.readManifest(sourceText, PackFileSet.MANIFEST) instanceof FileRead.Read<PackManifestDso> read
                        ? PackManifests.stale(read.value(), file -> optionalBytes(source.resolve(file)))
                        : List.of();
        List<String> writtenStale =
                yaml.readManifest(writtenText, PackFileSet.MANIFEST) instanceof FileRead.Read<PackManifestDso> stamped
                        ? PackManifests.stale(
                                stamped.value(),
                                file -> outcomes.get(file) instanceof WholePackRoundTrip.Written w
                                        ? optionalBytes(target.resolve(w.writtenPath()))
                                        : Optional.empty())
                        : List.of();
        return new FileOutcome.Written(writtenPath, sourceTree, writtenTree, sourceStale, writtenStale);
    }

    /** A text of the Markdown form, read into its model, or the reader's refusal naming its line. */
    private static Reading markdown(String text) {
        return switch (MarkdownCodec.parse(text)) {
            case MarkdownCodec.Parsed parsed -> new Reading.Markdown(parsed.document());
            case MarkdownRefusal refusal -> new Reading.MarkdownRefused(refusal.message());
        };
    }

    /** Every regular file under {@code target}, target-relative and {@code /}-separated; none when it is missing. */
    private static SortedSet<String> writtenFiles(Path target) {
        if (!Files.isDirectory(target)) {
            return new TreeSet<>();
        }
        try (Stream<Path> files = Files.walk(target)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> target.relativize(file)
                            .toString()
                            .replace(file.getFileSystem().getSeparator(), "/"))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<byte[]> optionalBytes(Path file) {
        return Files.isRegularFile(file) ? FilesystemPackStore.readBytes(file) : Optional.empty();
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
