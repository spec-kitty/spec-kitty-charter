package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.OwnerOnly;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.PackTreeReader.Sink;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.SourceRefusal;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The local path source (FR-004, IC-05, research R9, squad finding 10): it copies the directory a relative path names
 * under {@code charter.pack-sources.filesystem.root} into staging through the bounded tree writer, the same path rules
 * and limits an archive meets.
 *
 * <ul>
 *   <li><b>Confined.</b> The path is walked one segment at a time with a {@link SecureDirectoryStream} opened on the
 *       root's real path, each directory opened relative to the one before it without following links, so a link at
 *       any segment, inside the pack or at its end, is refused and no {@code ..} can climb out. The tree is read the
 *       same way.
 *   <li><b>Regular files with one link.</b> Only regular files whose link count is one are read, and the file opened
 *       is checked to be the file the directory listed (its file key is the same before and after the open and on the
 *       path the link count was read from), so a hard link to a file outside the root, or a swap during the read, is
 *       refused. Directories are copied; a link, a device or a socket is refused.
 *   <li><b>Refused at startup.</b> A root under {@code /proc}, {@code /sys} or {@code /dev}, or one that overlaps the
 *       configuration root in either direction, is refused when the reader is built; the root is checked again on its
 *       real path at every fetch.
 *   <li><b>Every refusal reads the same.</b> A missing path, a file, a link, a path that leaves the root, a hard link,
 *       an entry or a limit an archive would refuse, an unreadable entry and an unconfigured root are one
 *       {@code path_refused}, whose detail names nothing of the path, so the answer does not reveal what exists. The
 *       operator's log says why, by the violation codes and the exception classes, never by a path the client chose.
 * </ul>
 */
public final class LocalSourceReader implements PackSourceFetcher {

    private static final System.Logger LOG = System.getLogger(LocalSourceReader.class.getName());

    private static final String EXTRACTED = "extract";
    private static final int MAX_PATH_CHARS = 4096;
    private static final int MAX_DEPTH = 32;
    private static final List<Path> KERNEL_TREES = List.of(Path.of("/proc"), Path.of("/sys"), Path.of("/dev"));
    private static final String ROOT_KEY = "charter.pack-sources.filesystem.root";
    private static final String CONFIG_KEY = "charter.config-root";

    private final @Nullable Path root;
    private final @Nullable Path configRoot;
    private final PackTreeReader reader;

    /**
     * A reader of the directories under a root.
     *
     * @param root the configured root, or null when none is set, and every path is then refused
     * @param configRoot the configuration root the root must not overlap, or null when none is set
     * @param reader the writer of the staged tree
     * @throws IllegalArgumentException when the root is under {@code /proc}, {@code /sys} or {@code /dev}, overlaps the
     *     configuration root, or lies on a file system that has no secure directory streams
     */
    public LocalSourceReader(@Nullable Path root, @Nullable Path configRoot, PackTreeReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.configRoot = configRoot == null ? null : realOrNormalised(configRoot);
        this.root = root == null ? null : requireAllowed(realOrNormalised(root));
        if (this.root != null) {
            requireSecureStreams(this.root);
        }
    }

    @Override
    public PackSourceKind kind() {
        return PackSourceKind.FILESYSTEM;
    }

    @Override
    public Staged fetch(PackSourceRequest request, Path directory) throws CharterRefusal {
        String path = requireWellFormed(request);
        List<String> segments = segmentsOf(path);
        if (root == null) {
            throw refused("no root is configured");
        }
        try {
            StagedPack staged = copy(segments, directory);
            return new Staged(staged, Map.of("kind", kind().wire(), "path", path));
        } catch (ArchiveRefusal violations) {
            throw refused(violations.violations().stream()
                    .map(violation -> violation.code().wire())
                    .distinct()
                    .collect(Collectors.joining(", ")));
        } catch (IOException | UnsupportedOperationException | ClassCastException unreadable) {
            throw refused(unreadable.getClass().getSimpleName());
        }
    }

    private static String requireWellFormed(PackSourceRequest request) {
        if (request.kind() != PackSourceKind.FILESYSTEM) {
            throw new IllegalArgumentException("not a filesystem source");
        }
        String path = request.path();
        if (path == null
                || request.url() != null
                || request.ref() != null
                || request.commit() != null
                || request.sha256() != null) {
            throw SourceRefusal.SOURCE_MALFORMED.refusal();
        }
        return path;
    }

    /** The segments of a relative, plain path: no empty, {@code .} or {@code ..} segment, no backslash, no NUL. */
    private List<String> segmentsOf(String path) {
        if (path.isEmpty()
                || path.length() > MAX_PATH_CHARS
                || path.indexOf('\\') >= 0
                || path.indexOf('\0') >= 0
                || path.startsWith("/")) {
            throw refused("the path is not a plain relative path");
        }
        List<String> segments = List.of(path.split("/", -1));
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw refused("the path is not a plain relative path");
            }
        }
        return segments;
    }

    private StagedPack copy(List<String> segments, Path directory) throws IOException, ArchiveRefusal {
        Path real = requireAllowed(Objects.requireNonNull(root).toRealPath());
        Path into = OwnerOnly.directories(directory.resolve(EXTRACTED));
        boolean staged = false;
        try (DirectoryStream<Path> top = Files.newDirectoryStream(real)) {
            StagedPack pack = copyBelow(secure(top), real, segments, into);
            staged = true;
            return pack;
        } finally {
            if (!staged) {
                // The writer leaves nothing in it on a refusal; the empty directory goes too.
                Files.deleteIfExists(into);
            }
        }
    }

    /** Opens each segment relative to the directory before it, never following a link, then stages the last one. */
    private StagedPack copyBelow(SecureDirectoryStream<Path> top, Path real, List<String> segments, Path into)
            throws IOException, ArchiveRefusal {
        List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
        try {
            SecureDirectoryStream<Path> current = top;
            Path here = real;
            for (String segment : segments) {
                current = current.newDirectoryStream(Path.of(segment), LinkOption.NOFOLLOW_LINKS);
                opened.add(current);
                here = here.resolve(segment);
            }
            SecureDirectoryStream<Path> pack = current;
            Path packPath = here;
            return reader.stage(sink -> new Walk(sink).directory(pack, packPath, "", 0), into);
        } finally {
            closeAll(opened);
        }
    }

    private static SecureDirectoryStream<Path> secure(DirectoryStream<Path> stream) throws IOException {
        if (stream instanceof SecureDirectoryStream<Path> secure) {
            return secure;
        }
        throw new IOException("the file system has no secure directory streams");
    }

    private static void closeAll(List<SecureDirectoryStream<Path>> opened) {
        for (SecureDirectoryStream<Path> stream : opened.reversed()) {
            try {
                stream.close();
            } catch (IOException ignored) {
                LOG.log(System.Logger.Level.DEBUG, "A directory stream of a filesystem source did not close.");
            }
        }
    }

    /** One walk of the pack directory into a sink; every entry is read relative to the directory it was listed in. */
    private static final class Walk {

        private final Sink sink;

        Walk(Sink sink) {
            this.sink = sink;
        }

        void directory(SecureDirectoryStream<Path> dir, Path dirPath, String prefix, int depth) throws IOException {
            if (depth > MAX_DEPTH) {
                throw new IOException("the tree is deeper than " + MAX_DEPTH + " directories");
            }
            for (Path entry : dir) {
                Path name = entry.getFileName();
                String relative = prefix.isEmpty() ? name.toString() : prefix + "/" + name;
                BasicFileAttributes attributes = attributesOf(dir, name);
                if (attributes.isDirectory()) {
                    sink.directory(relative);
                    try (SecureDirectoryStream<Path> child = dir.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                        directory(child, dirPath.resolve(name.toString()), relative, depth + 1);
                    }
                } else if (attributes.isRegularFile()) {
                    file(dir, dirPath.resolve(name.toString()), name, relative, attributes);
                } else {
                    throw new IOException("an entry is a link or another kind of file");
                }
            }
        }

        private void file(
                SecureDirectoryStream<Path> dir, Path path, Path name, String relative, BasicFileAttributes listed)
                throws IOException {
            try (SeekableByteChannel channel =
                    dir.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                BasicFileAttributes opened = attributesOf(dir, name);
                requireSameFile(listed, opened);
                requireOneLink(path, opened);
                sink.file(relative, channel.size(), Channels.newInputStream(channel));
            }
        }

        private static BasicFileAttributes attributesOf(SecureDirectoryStream<Path> dir, Path name) throws IOException {
            return dir.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                    .readAttributes();
        }

        private static void requireSameFile(BasicFileAttributes listed, BasicFileAttributes opened) throws IOException {
            Object key = listed.fileKey();
            if (!opened.isRegularFile() || key == null || !key.equals(opened.fileKey())) {
                throw new IOException("a file changed while it was opened");
            }
        }

        /** The link count of the file as the path shows it, which must be the one file opened and have one link. */
        private static void requireOneLink(Path path, BasicFileAttributes opened) throws IOException {
            Object links = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes byPath =
                    Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!Integer.valueOf(1).equals(links) || !Objects.equals(byPath.fileKey(), opened.fileKey())) {
                throw new IOException("a file has another hard link or changed while it was checked");
            }
        }
    }

    private static CharterRefusal refused(String why) {
        LOG.log(System.Logger.Level.INFO, "A filesystem source was refused: {0}", why);
        return SourceRefusal.PATH_REFUSED.refusal();
    }

    private static Path realOrNormalised(Path path) {
        Path normalised = path.toAbsolutePath().normalize();
        try {
            return normalised.toRealPath();
        } catch (IOException notThere) {
            return normalised;
        }
    }

    private Path requireAllowed(Path real) {
        for (Path tree : KERNEL_TREES) {
            if (real.startsWith(tree)) {
                throw new IllegalArgumentException(ROOT_KEY + " must not be under /proc, /sys or /dev");
            }
        }
        if (configRoot != null && (real.startsWith(configRoot) || configRoot.startsWith(real))) {
            throw new IllegalArgumentException(ROOT_KEY + " must not overlap " + CONFIG_KEY + ": the packs and the"
                    + " staging live under it, and a source must never read what the service stores");
        }
        return real;
    }

    private static void requireSecureStreams(Path real) {
        if (!Files.isDirectory(real)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(real)) {
            if (!(stream instanceof SecureDirectoryStream)) {
                throw new IllegalArgumentException(
                        ROOT_KEY + " lies on a file system without secure directory streams");
            }
        } catch (IOException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "The filesystem source root cannot be listed yet.", unreadable);
        }
    }
}
