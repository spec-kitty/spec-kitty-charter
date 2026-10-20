package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.ParseFailure;
import ai.speckitty.charter.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackElementRepository;
import ai.speckitty.charter.domain.PackElementViolation;
import ai.speckitty.charter.domain.PackLocator;
import ai.speckitty.charter.domain.PackSnapshot;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Tactic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The filesystem {@link PackElementRepository}: it reads a pack directory partial-tolerantly and writes a descriptor
 * or tactic back in place, atomically and inside the pack.
 *
 * <p>The domain models carry no storage coordinate, so the adapter owns id to path: a tactic is located by scanning
 * the pack's {@code *.yaml} files that {@link PackFileKind} classifies as {@code tactic} for the one whose {@code id}
 * matches. Fetch never throws on the content of a file: a file that is not UTF-8, not YAML or breaks its schema is a
 * {@link PackElementViolation}. Only a missing or unreadable {@code pack.yaml} refuses the fetch
 * ({@code PACK_UNREADABLE}), since a snapshot cannot exist without its descriptor.
 *
 * <p>A write serializes from scratch, writes a temporary file beside the target and renames it over the target, so a
 * failure leaves the original untouched. A resolved target outside the pack root is refused.
 */
public final class FilesystemPackElementRepository implements PackElementRepository {

    private static final String PACK_YAML = "pack.yaml";
    private static final String TACTIC_KIND = "tactic";

    /**
     * The failure-injection seam: the second step of a write, moving the fully written temporary file over the
     * target. The default is an {@code ATOMIC_MOVE}. A test supplies a strategy that throws (or does nothing) to
     * prove that a failure between "temporary file written" and "renamed" leaves the target unchanged and no
     * temporary file behind.
     */
    @FunctionalInterface
    interface Rename {
        /**
         * Moves {@code temp} over {@code target}.
         *
         * @param temp the fully written temporary file
         * @param target the file to replace
         * @throws IOException when the move fails
         */
        void move(Path temp, Path target) throws IOException;
    }

    private static final Rename ATOMIC = (temp, target) -> Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);

    private final Rename rename;

    /** A repository that renames atomically. */
    public FilesystemPackElementRepository() {
        this(ATOMIC);
    }

    /** A repository with an injected rename step; the seam for tests of write atomicity. */
    FilesystemPackElementRepository(Rename rename) {
        this.rename = Objects.requireNonNull(rename, "rename");
    }

    @Override
    public PackSnapshot fetch(PackLocator location) {
        Path directory = directory(location);
        List<PackElementViolation> violations = new ArrayList<>();
        Path packYaml = directory.resolve(PACK_YAML);
        if (!Files.isRegularFile(packYaml)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name(directory) + " has no pack.yaml");
        }
        PackDescriptor descriptor = switch (readFile(packYaml, PACK_YAML, PackDescriptorReader::read)) {
            case FileRead.Read<PackDescriptor> good -> good.value();
            case FileRead.Unreadable<PackDescriptor> bad ->
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack.yaml of " + name(directory) + " cannot be read");
            case FileRead.Absent<PackDescriptor> absent ->
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name(directory) + " has no pack.yaml");
        };
        List<Tactic> tactics = new ArrayList<>();
        for (String file : tacticFiles(directory)) {
            switch (readFile(directory.resolve(file), file, text -> TacticReader.read(text, file))) {
                case FileRead.Read<Tactic> good -> tactics.add(good.value());
                case FileRead.Unreadable<Tactic> bad -> violations.addAll(violationsOf(bad));
                case FileRead.Absent<Tactic> absent -> {}
            }
        }
        return new PackSnapshot(descriptor, tactics, violations);
    }

    @Override
    public void write(PackLocator location, Tactic tactic) {
        Path directory = directory(location);
        Path target = locateTactic(directory, tactic.id());
        writeAtomically(directory, target, TacticWriter.write(tactic));
    }

    @Override
    public void write(PackLocator location, PackDescriptor descriptor) {
        Path directory = directory(location);
        writeAtomically(directory, directory.resolve(PACK_YAML), PackDescriptorWriter.write(descriptor));
    }

    // ---- id to path ----------------------------------------------------------------------------------------------

    /** The existing tactic file whose {@code id} is {@code id}; the first in path order if several declare it. */
    private static Path locateTactic(Path directory, String id) {
        for (String file : tacticFiles(directory)) {
            ParsedYaml parsed =
                    ParsedYaml.parse(readText(directory.resolve(file)).orElse(""), file);
            if (parsed.document() instanceof Map<?, ?> map && id.equals(map.get("id"))) {
                return directory.resolve(file);
            }
        }
        throw new CharterRefusal(
                RefusalCode.PACK_UNREADABLE, "pack " + name(directory) + " has no tactic with id '" + id + "'");
    }

    /** The pack-relative, slash-separated paths of the pack's tactic files, in path order. */
    private static List<String> tacticFiles(Path directory) {
        // The classification globs are relative to the directory that holds the pack directories.
        Path last = directory.toAbsolutePath().normalize().getFileName();
        String prefix = (last == null ? "pack" : last.toString()) + "/";
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".yaml") && Files.isRegularFile(p))
                    .map(p -> directory
                            .relativize(p)
                            .toString()
                            .replace(p.getFileSystem().getSeparator(), "/"))
                    .filter(file -> PackFileKind.bundled().classify(prefix + file) instanceof PackFileKind.Mapped mapped
                            && TACTIC_KIND.equals(mapped.kind()))
                    .sorted()
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw FileSystemPackInspector.unlistable(name(directory), e);
        }
    }

    // ---- reading -------------------------------------------------------------------------------------------------

    private static <T> FileRead<T> readFile(Path path, String file, Function<String, FileRead<T>> reader) {
        Optional<String> text = readText(path);
        if (text.isEmpty()) {
            return new FileRead.Unreadable<>(
                    file, new ParseFailure(file, "the file cannot be read as UTF-8 text"), List.of());
        }
        return reader.apply(text.get());
    }

    /** The file's text; empty when its bytes are not UTF-8 or it cannot be read. */
    private static Optional<String> readText(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return Optional.of(StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static List<PackElementViolation> violationsOf(FileRead.Unreadable<?> bad) {
        if (bad.parse() != null) {
            return List.of(new PackElementViolation(
                    bad.file(), "unreadable", bad.parse().firstLine()));
        }
        List<PackElementViolation> out = new ArrayList<>();
        for (SchemaViolation v : bad.violations()) {
            String where = v.pointer().isEmpty() ? "the document" : "/" + String.join("/", v.pointer());
            out.add(new PackElementViolation(bad.file(), "schema", v.keyword() + " violated at " + where));
        }
        return out;
    }

    // ---- writing -------------------------------------------------------------------------------------------------

    /**
     * Writes {@code content} to a temporary file beside {@code target}, then renames it over {@code target} through
     * the {@link Rename} seam. A target outside the pack root is refused before anything is written; a failure at
     * either step removes the temporary file and leaves {@code target} as it was.
     */
    private void writeAtomically(Path directory, Path target, String content) {
        Path root = directory.toAbsolutePath().normalize();
        Path resolved = target.toAbsolutePath().normalize();
        if (!resolved.startsWith(root) || !realPathContained(root, resolved)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "refusing to write outside pack " + name(directory));
        }
        @Nullable Path temp = null;
        try {
            temp = Files.createTempFile(resolved.getParent(), ".pack-write-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            rename.move(temp, resolved);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot write " + root.relativize(resolved) + " in pack", e);
        }
    }

    /** Whether the target's real location, following symlinks, is inside the pack's real root. */
    private static boolean realPathContained(Path root, Path target) {
        try {
            Path realRoot = root.toRealPath();
            Path existing = target;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            return existing != null && existing.toRealPath().startsWith(realRoot);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteQuietly(@Nullable Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // the original failure is the one worth reporting
        }
    }

    private static Path directory(PackLocator location) {
        Path directory = Path.of(location.locator());
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name(directory) + " is not a directory");
        }
        return directory;
    }

    private static String name(Path directory) {
        Path last = directory.toAbsolutePath().normalize().getFileName();
        return last == null ? "pack" : last.toString();
    }
}
