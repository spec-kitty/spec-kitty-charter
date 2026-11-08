package ai.speckitty.charter.packs.application;

/** The archive formats a pack upload may use; the request's {@code Content-Type} picks one (IC-03). */
public enum ArchiveFormat {
    /** A gzip-compressed tar ({@code application/gzip}, {@code application/x-gzip}, {@code application/x-tar+gzip}). */
    TAR_GZ,
    /** A zip ({@code application/zip}). */
    ZIP
}
