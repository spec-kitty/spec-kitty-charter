/**
 * The bounded, path-safe pack archive extractor (IC-03, NFR-004, NFR-005): the adapter of the
 * {@code PackArchiveReader} port. It reads {@code tar.gz} and {@code zip}, counts every byte it reads, and writes only
 * regular files and directories inside a staging root. The application ring names only the port.
 */
@NullMarked
package ai.speckitty.charter.packs.infra.archive;

import org.jspecify.annotations.NullMarked;
