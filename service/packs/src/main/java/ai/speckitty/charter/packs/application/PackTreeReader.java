package ai.speckitty.charter.packs.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * The port that turns a tree a source walked, rather than an archive, into a staged pack directory (IC-05): the
 * same bounded, path-safe writer the archive extractor uses (IC-03), so a local directory meets the same path rules
 * and limits as an archive does. The adapter lives in {@code packs.infra.archive}; the application ring names only
 * this interface.
 */
public interface PackTreeReader {

    /** Walks a tree, telling the sink each directory and file in turn, a directory before what it holds. */
    @FunctionalInterface
    interface TreeWalk {

        /**
         * Walks the tree.
         *
         * @param sink where each entry goes
         * @throws IOException when the tree cannot be read
         */
        void walk(Sink sink) throws IOException;
    }

    /** One entry at a time of a walked tree; the path is relative to the tree and slash-separated. */
    interface Sink {

        /**
         * A directory.
         *
         * @param path its relative path
         * @throws IOException when the tree cannot be read
         */
        void directory(String path) throws IOException;

        /**
         * A regular file.
         *
         * @param path its relative path
         * @param size the size it declared when it was opened
         * @param data its bytes, read once and not closed; reading more than {@code size} bytes is a limit breach
         * @throws IOException when the tree cannot be read
         */
        void file(String path, long size, InputStream data) throws IOException;
    }

    /**
     * Stages the walked tree into a fresh directory inside the staging root, or refuses it.
     *
     * @param walk the tree
     * @param stagingRoot an existing directory owned by the caller; the writer writes only inside it
     * @return the staged pack
     * @throws ArchiveRefusal when the tree is over a limit or any entry breaks a path rule; nothing is left in the
     *     staging root
     * @throws IOException when the tree cannot be read; nothing is left in the staging root
     */
    StagedPack stage(TreeWalk walk, Path stagingRoot) throws ArchiveRefusal, IOException;
}
