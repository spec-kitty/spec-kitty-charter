package ai.speckitty.charter.packs.infra.storage;

import static org.junit.jupiter.api.Assumptions.abort;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Symbolic links for the link tests: made where the file system allows one. Where it does not, the test is skipped;
 * when the test JVM's {@value #REQUIRED} is {@code true}, as CI sets it ({@code -PrequireSymlinks=true}), it fails
 * instead, so a runner that cannot make links does not stay green by skipping them (#286).
 */
final class SymbolicLinks {

    /** The system property that makes link support mandatory. */
    static final String REQUIRED = "charter.requireSymlinks";

    /** One attempt to make a link. */
    @FunctionalInterface
    interface LinkMaker {
        void make() throws IOException;
    }

    private SymbolicLinks() {}

    /**
     * Links {@code link} to {@code target}, or skips (fails, when links are required) the test.
     *
     * @return the link
     */
    static Path create(Path link, Path target) throws IOException {
        make(required(), () -> Files.createSymbolicLink(link, target));
        return link;
    }

    /** Checks that links can be made in {@code dir}, or skips (fails, when links are required) the test. */
    static void requireSupport(Path dir) throws IOException {
        Path target = Files.createTempFile(dir, "probe", ".tmp");
        Path link = dir.resolve(target.getFileName() + ".lnk");
        try {
            create(link, target);
        } finally {
            Files.deleteIfExists(link);
            Files.delete(target);
        }
    }

    /** Whether this test JVM was told that link support is mandatory. */
    static boolean required() {
        return Boolean.getBoolean(REQUIRED);
    }

    /** Runs {@code maker}; a file system that cannot make the link skips the test, or fails it when {@code required}. */
    static void make(boolean required, LinkMaker maker) throws IOException {
        try {
            maker.make();
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            abort("the file system cannot create symbolic links: " + noLinks);
        }
    }
}
