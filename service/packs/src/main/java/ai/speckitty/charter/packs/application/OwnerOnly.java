package ai.speckitty.charter.packs.application;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What the service writes to disk before it is stored, made the owner's alone: the staging root, a job's directory,
 * the uploaded or fetched archive, the tree extracted from it, a git work directory and the home of the git version
 * check (plan, security squad 8). A staged upload is untrusted input of one client, and the process may share a host or
 * a volume with others.
 *
 * <p>This is the only place in the module that builds a POSIX permission attribute (#510). Where the file system has
 * POSIX permissions, a directory is created with mode {@code 0700} and a file with {@code 0600}, at creation. Where it
 * has none but has ACLs (NTFS, on Windows), it is created without the attribute and, before it is used, given an ACL
 * with one entry that allows its owner everything; a directory's entry is inherited by what is created in it. Where
 * the file system has neither, nothing is created and {@link UnsupportedFileSystemException} says why.
 *
 * <p>Limits of the ACL path, acceptable for short-lived staging: the ACL replaces the inherited one but is not marked
 * protected, so a later inheritance propagation from a parent can add entries back; and its one entry goes to the
 * path's owner, which under an elevated Windows token is {@code BUILTIN\Administrators} rather than the user.
 */
public final class OwnerOnly {

    private static final FileAttribute<Set<PosixFilePermission>> DIRECTORY_MODE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    private static final FileAttribute<Set<PosixFilePermission>> FILE_MODE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    private static final Set<OpenOption> NEW_FILE =
            Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);

    private OwnerOnly() {}

    /** How a file system can make a path the owner's alone. */
    enum Protection {
        /** POSIX permissions, set at creation. */
        POSIX,
        /** An ACL with one entry for the owner, set right after creation. */
        ACL,
        /** Neither: the file system cannot keep a path from other users. */
        NONE;

        /**
         * Asks the store the path is (or would be) on, through its nearest existing ancestor: a Windows file system
         * lists {@code acl} for every volume, but a FAT or exFAT volume keeps no ACL.
         */
        static Protection of(Path path) {
            Path at = path.toAbsolutePath();
            while (at != null && !Files.exists(at, LinkOption.NOFOLLOW_LINKS)) {
                at = at.getParent();
            }
            if (at == null) {
                return of(path.getFileSystem().supportedFileAttributeViews());
            }
            try {
                FileStore store = Files.getFileStore(at);
                if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
                    return POSIX;
                }
                return store.supportsFileAttributeView(AclFileAttributeView.class) ? ACL : NONE;
            } catch (IOException unreadable) {
                return of(path.getFileSystem().supportedFileAttributeViews());
            }
        }

        private static Protection of(Set<String> views) {
            if (views.contains("posix")) {
                return POSIX;
            }
            return views.contains("acl") ? ACL : NONE;
        }
    }

    /**
     * Refuses, at start-up, a directory whose file system can keep nothing from other users, so that a registration
     * never finds out with a {@code 500}.
     *
     * @param directory the directory the service will create owner-only files in
     * @throws IllegalStateException when its file system has neither POSIX permissions nor ACLs
     */
    public static void requireSupported(Path directory) {
        if (Protection.of(directory) == Protection.NONE) {
            throw new IllegalStateException(UnsupportedFileSystemException.message(directory));
        }
    }

    /**
     * Creates the directory and every parent that is missing, each owner-only. A directory that exists is left as it
     * is.
     *
     * @param directory the directory
     * @return the directory
     * @throws IOException when it cannot be made, or its file system cannot make it owner-only
     */
    public static Path directories(Path directory) throws IOException {
        return directories(directory, OwnerOnly::restrict);
    }

    static Path directories(Path directory, Restriction restriction) throws IOException {
        if (protection(directory) == Protection.POSIX) {
            return Files.createDirectories(directory, DIRECTORY_MODE);
        }
        Deque<Path> missing = new ArrayDeque<>();
        for (Path at = directory.toAbsolutePath(); at != null && !Files.isDirectory(at); at = at.getParent()) {
            missing.push(at);
        }
        for (Path next : missing) {
            try {
                Files.createDirectory(next);
            } catch (FileAlreadyExistsException raced) {
                if (!Files.isDirectory(next)) {
                    throw raced;
                }
                continue;
            }
            restrictOrRemove(next, restriction);
        }
        return directory;
    }

    /**
     * Creates one owner-only directory, whose parent must exist.
     *
     * @param directory the directory, which must not exist
     * @return the directory
     * @throws IOException when it exists, cannot be made, or its file system cannot make it owner-only
     */
    public static Path directory(Path directory) throws IOException {
        if (protection(directory) == Protection.POSIX) {
            return Files.createDirectory(directory, DIRECTORY_MODE);
        }
        Files.createDirectory(directory);
        restrictOrRemove(directory, OwnerOnly::restrict);
        return directory;
    }

    /**
     * Creates an owner-only directory with a new name in {@code parent}.
     *
     * @param parent the directory to create it in
     * @param prefix the start of its name
     * @return the directory
     * @throws IOException when it cannot be made, or its file system cannot make it owner-only
     */
    public static Path temporaryDirectory(Path parent, String prefix) throws IOException {
        if (protection(parent) == Protection.POSIX) {
            return Files.createTempDirectory(parent, prefix, DIRECTORY_MODE);
        }
        Path made = Files.createTempDirectory(parent, prefix);
        restrictOrRemove(made, OwnerOnly::restrict);
        return made;
    }

    /**
     * Creates an owner-only directory with a new name in the host's temporary directory.
     *
     * @param prefix the start of its name
     * @return the directory
     * @throws IOException when it cannot be made, or its file system cannot make it owner-only
     */
    public static Path temporaryDirectory(String prefix) throws IOException {
        return temporaryDirectory(Path.of(System.getProperty("java.io.tmpdir")), prefix);
    }

    /**
     * Opens a new owner-only file for writing, never following a link and never replacing a file.
     *
     * @param file the file, which must not exist
     * @return the stream to write it with; the caller closes it
     * @throws IOException when the file exists, is a link, cannot be made, or cannot be made owner-only
     */
    public static OutputStream newFile(Path file) throws IOException {
        if (protection(file) == Protection.POSIX) {
            return Channels.newOutputStream(Files.newByteChannel(file, NEW_FILE, FILE_MODE));
        }
        SeekableByteChannel channel = Files.newByteChannel(file, NEW_FILE);
        try {
            restrict(file, false);
        } catch (IOException | RuntimeException failure) {
            try (channel) {
                Files.deleteIfExists(file);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        return Channels.newOutputStream(channel);
    }

    /** How a path is made the owner's alone where there are no POSIX permissions; a test substitutes a failure. */
    @FunctionalInterface
    interface Restriction {
        void apply(Path path, boolean directory) throws IOException;
    }

    /**
     * Restricts a directory just created. When that fails the directory is removed (if it is empty) before the failure
     * is rethrown: left behind with the ACL it inherited, a later {@link #directories} call would find it and skip it
     * as one that exists, and the retry would use an unrestricted directory.
     */
    private static void restrictOrRemove(Path created, Restriction restriction) throws IOException {
        try {
            restriction.apply(created, true);
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(created);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static Protection protection(Path path) throws UnsupportedFileSystemException {
        Protection protection = Protection.of(path);
        if (protection == Protection.NONE) {
            throw new UnsupportedFileSystemException(path);
        }
        return protection;
    }

    /**
     * Replaces the path's ACL with one entry that allows its owner everything. A directory is created before its ACL is
     * set, so in a parent other users can write to (a shared temporary directory) one of them could plant a file in it
     * first, which the new ACL would not remove: a directory that is not empty once restricted is refused. That closes
     * only the planted-entry part of the window: a handle another user opened on the path before the ACL was set stays
     * valid, and this check cannot see it.
     */
    static void restrict(Path path, boolean directory) throws IOException {
        AclFileAttributeView view =
                Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new UnsupportedFileSystemException(path);
        }
        AclEntry.Builder owner = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(view.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) {
            owner.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        }
        view.setAcl(List.of(owner.build()));
        if (directory) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                if (entries.iterator().hasNext()) {
                    throw new FileSystemException(
                            path.toString(), null, "a new owner-only directory held an entry before it was restricted");
                }
            }
        }
    }

    /** The file system of a path has neither POSIX permissions nor ACLs, so the path cannot be made owner-only. */
    public static final class UnsupportedFileSystemException extends IOException {

        private static final long serialVersionUID = 1L;

        UnsupportedFileSystemException(Path path) {
            super(message(path));
        }

        static String message(Path path) {
            Path named = Objects.requireNonNull(path, "path");
            return "The file system of " + named + " supports neither POSIX permissions nor ACLs, so the service"
                    + " cannot keep what it stages there from other users; use a directory on a file system that"
                    + " supports one of them.";
        }
    }
}
