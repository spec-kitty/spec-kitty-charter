package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.packs.domain.ClassifiedFile;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The storage edge's account of one read of a pack tree (#554, S3, FR-016): the one owner of "held". The storage rules,
 * the store's load, the whole-pack round-trip gate and, through the readout, validate mode and a registration all read
 * it; nothing walks or classifies the tree again.
 *
 * @param files every regular file the read classified, with its kind or its exclusion, in the reader's order
 * @param notHeld every regular file of the tree that is not itself a link and that the store does not hold, path-sorted,
 *     each with its explicit kind and its reason: an excluded file, a file no pack file kind models, and a
 *     {@code policy.yaml} the descriptor does not name (open case 9). A retired file is never one: it is a read fault
 * @param manifest the {@code pack-manifest.yaml} facts
 * @param reservedShard the registered relationships' shard, as a fact (C-4)
 * @param entries every entry of the walk, each regular file that is not itself a link and each link, path-sorted, with
 *     how it stands: what the whole-pack gate accounts for, a pack whose descriptor cannot be read included
 * @param companions every companion file a readable artifact names inside the pack, whether or not the file is there,
 *     path-sorted, each once (its first owner): what the gate pairs a companion with
 */
public record TreeCensus(
        List<ClassifiedFile> files,
        List<NotHeldFile> notHeld,
        Manifest manifest,
        ReservedShard reservedShard,
        List<Entry> entries,
        List<Companion> companions) {

    /**
     * Copies the lists and checks the fields.
     *
     * @param files the classified files
     * @param notHeld the files not held
     * @param manifest the manifest facts
     * @param reservedShard the reserved shard fact
     * @param entries every entry of the walk
     * @param companions every companion an artifact names
     */
    public TreeCensus {
        files = List.copyOf(files);
        notHeld = List.copyOf(notHeld);
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(reservedShard, "reservedShard");
        entries = List.copyOf(entries);
        companions = List.copyOf(companions);
    }

    /**
     * The census of a tree the read could not enter: no file, no manifest, no reserved shard, no entry.
     *
     * @return the empty census
     */
    public static TreeCensus empty() {
        return new TreeCensus(List.of(), List.of(), Manifest.ABSENT, ReservedShard.ABSENT, List.of(), List.of());
    }

    /** How an entry of the walk stands, as the file-kinds table and the companion rule classify it. */
    public enum Standing {
        /** A symbolic link, never followed. */
        LINK,
        /** A file a {@code files} rule maps to a schema. */
        MAPPED,
        /** A companion a readable artifact names: it stands with its owner, whatever rule maps it. */
        COMPANION,
        /** A file a {@code retired} rule refuses by name. */
        RETIRED,
        /** A file an {@code exclusions} rule, or the {@code ignored} fallback, takes. */
        EXCLUDED,
        /** A file no rule covers. */
        UNCOVERED
    }

    /**
     * One entry of the walk.
     *
     * @param path the pack-relative path, {@code /}-separated
     * @param standing how it stands
     * @param name the schema a rule maps it to; its owner's schema for a companion; the matching glob for a retired
     *     file; the exclusion class for an excluded one; the first path segment (the file name at the root) for a file
     *     no rule covers; empty for a link
     * @param form the content form token ({@code file-kinds.yaml}'s {@code content_form}) a mapped file's rule
     *     declares, or a companion's declared form; empty for any other entry
     */
    public record Entry(String path, Standing standing, String name, Optional<String> form) {
        /**
         * Checks the fields.
         *
         * @param path the path
         * @param standing how it stands
         * @param name its name
         * @param form its content form token
         */
        public Entry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(standing, "standing");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(form, "form");
        }
    }

    /**
     * A companion file a readable artifact names (ADR-0039, #194).
     *
     * @param path the companion's pack-relative path
     * @param owner the owning artifact's pack-relative file
     * @param ownerKind the owner's schema
     * @param declared the companion path exactly as the owner declares it, relative to the owner's file
     * @param form the companion's declared content form token
     */
    public record Companion(String path, String owner, String ownerKind, String declared, String form) {
        /**
         * Checks the fields.
         *
         * @param path the path
         * @param owner the owner's file
         * @param ownerKind the owner's schema
         * @param declared the declared path
         * @param form the declared form token
         */
        public Companion {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(ownerKind, "ownerKind");
            Objects.requireNonNull(declared, "declared");
            Objects.requireNonNull(form, "form");
        }
    }

    /** What stands at the reserved shard's path ({@code registered.graph.yaml}), read without following a link. */
    public enum ReservedShard {
        /** No regular file is there. */
        ABSENT,
        /** A registration wrote it: it carries the registration's stamp. */
        STAMPED,
        /** A regular file is there that no registration wrote: a source shard under the reserved name. */
        UNSTAMPED
    }

    /**
     * The manifest's facts (#110, #630, ADR-0042): whether something stands at its path, whether that is a regular file
     * (read without following a link), the lists it declares when it is readable, the lists the reader derives from
     * what the pack holds ({@code PackManifestDerivation}), and the derived fields that do not verify against the
     * incoming bytes ({@code PackManifests.stale}), so a rule compares facts and derives nothing.
     *
     * @param present whether anything stands at {@code pack-manifest.yaml}
     * @param regular whether that is a regular file, not a directory or a link
     * @param listed the lists the manifest declares; empty when it is absent or unreadable
     * @param derived the lists derived from what the pack holds; empty when nothing is listed, or the pack cannot be
     *     loaded
     * @param stale the derived fields of a readable manifest that do not verify against the pack's bytes, in the
     *     manifest's order (each constituent's {@code content_hash}, each preset's, then {@code manifest_hash}); never a
     *     listed path with no file, which membership owns; empty when it is absent or unreadable
     */
    public record Manifest(
            boolean present,
            boolean regular,
            Optional<Entries> listed,
            Optional<Entries> derived,
            List<StaleField> stale) {

        /** No manifest at all. */
        public static final Manifest ABSENT = new Manifest(false, false, Optional.empty(), Optional.empty());

        /**
         * Checks the fields and copies the list.
         *
         * @param present whether anything stands at the path
         * @param regular whether it is a regular file
         * @param listed the declared lists
         * @param derived the derived lists
         * @param stale the stale derived fields
         */
        public Manifest {
            Objects.requireNonNull(listed, "listed");
            Objects.requireNonNull(derived, "derived");
            stale = List.copyOf(stale);
            if (regular && !present) {
                throw new IllegalArgumentException("a manifest that is a regular file is present");
            }
        }

        /**
         * The facts of a manifest whose derived fields all verify.
         *
         * @param present whether anything stands at the path
         * @param regular whether it is a regular file
         * @param listed the declared lists
         * @param derived the derived lists
         */
        public Manifest(boolean present, boolean regular, Optional<Entries> listed, Optional<Entries> derived) {
            this(present, regular, listed, derived, List.of());
        }
    }

    /**
     * One derived field of the manifest that does not verify against the incoming bytes (#630, ADR-0042 decision 5's
     * recipes).
     *
     * @param field the field as the manifest's verification names it: {@code constituents{<path>}.content_hash},
     *     {@code presets{<path>}.content_hash} or {@code manifest_hash}
     * @param place the JSON pointer segments of the field inside the manifest, for example
     *     {@code [constituents, 3, content_hash]}
     * @param file the pack-relative file whose bytes a {@code content_hash} does not match; empty for
     *     {@code manifest_hash}
     */
    public record StaleField(String field, List<String> place, Optional<String> file) {
        /**
         * Checks the fields and copies the list.
         *
         * @param field the field
         * @param place the pointer segments
         * @param file the file it hashes
         */
        public StaleField {
            Objects.requireNonNull(field, "field");
            place = List.copyOf(place);
            Objects.requireNonNull(file, "file");
        }
    }

    /**
     * A manifest's two lists, each entry in the manifest's own words: a constituent as {@code <kind>:<id> at <path>}, a
     * preset entry as {@code <id> at <path>}, each list in the manifest's order. Neither a kind token nor an id holds a
     * space, so the path is what follows an entry's first {@value #AT} ({@link #pathOf}).
     *
     * @param constituents the constituents
     * @param presets the preset entries
     */
    public record Entries(List<String> constituents, List<String> presets) {
        /**
         * Copies the lists.
         *
         * @param constituents the constituents
         * @param presets the preset entries
         */
        public Entries {
            constituents = List.copyOf(constituents);
            presets = List.copyOf(presets);
        }

        /** What separates an entry's kind and id from its path. */
        public static final String AT = " at ";

        /**
         * The path an entry names, the inverse of how the entry is written.
         *
         * @param entry a constituent or a preset entry, in the manifest's own words
         * @return its pack-relative path
         */
        public static String pathOf(String entry) {
            int at = entry.indexOf(AT);
            if (at < 0) {
                throw new IllegalArgumentException("not a manifest entry: " + entry);
            }
            return entry.substring(at + AT.length());
        }
    }
}
