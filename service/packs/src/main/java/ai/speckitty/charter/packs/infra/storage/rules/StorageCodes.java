package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.rules.DescriptorChecks;
import java.util.List;
import java.util.Set;

/**
 * The codes only the storage edge emits (#554, R2, S3): the read faults of a pack tree, the format prechecks of its
 * descriptor and assets (S2), and the holding codes of the tree (not held, the reserved shard, manifest membership, a
 * link in the tree).
 * Each is a kernel {@link ReasonCode} that owns its severity, and each token is built exactly once: a token a core rule
 * emits is a {@link PackCodes} constant, which the storage edge names where it reports the same fault. The wording of a
 * fault is {@link PackCodes#detail}, the one wording of every surface.
 *
 * <p>Where a contract enum already spells a token ({@link RefusalCode#PACK_UNREADABLE},
 * {@link Diagnostic.Code#FILE_NOT_HELD}), the constant is built from that spelling.
 */
public final class StorageCodes {

    /**
     * The pack's descriptor is missing, is not YAML or breaks its schema; or the pack root cannot be read. The closure
     * checks state the same token, so it is built once, in {@link PackCodes#PACK_UNREADABLE} (#556).
     */
    public static final ReasonCode PACK_UNREADABLE = PackCodes.PACK_UNREADABLE;

    /**
     * The store cannot assemble the pack as a whole: an aggregate invariant its readable artifacts break (R2 row 26),
     * placed at the pack after the graph faults. No planted pack reaches it: the read withholds what would breach. The
     * closure checks state the same token, so it is built once, in {@link PackCodes#VALIDATION_FAILED} (#556).
     */
    public static final ReasonCode ASSEMBLY_REFUSED = PackCodes.VALIDATION_FAILED;

    /**
     * A pack directory whose name is not a pack id ({@code PackIdentifier#isPackId}, #583, D5): the store never wrote
     * it, so its read refuses it rather than serve it under another name. The listing does not serve it, and a read of
     * it by its directory name (validate mode, a whole-pack load) states this one fault at the pack.
     */
    public static final ReasonCode PACK_ID_INVALID = violation("pack_id_invalid");

    /** A file's bytes cannot be read, are not UTF-8, or do not parse; or the manifest is not a regular file. */
    public static final ReasonCode UNREADABLE = violation("unreadable");

    /** A file the file-kinds table retires: refused by name, never read (ADR-0044 decision 6). */
    public static final ReasonCode RETIRED = violation("retired");

    /**
     * A root file that differs only in letter case from a root rule's file ({@code Pack.yaml}, {@code Readme.md}): refused
     * by name under that rule, never read (ADR-0073 decision 3, #624).
     */
    public static final ReasonCode CASE_VARIANT = violation("case_variant");

    /** A companion file an artifact names cannot be read, leaves the pack, or is named by two artifacts. */
    public static final ReasonCode COMPANION = violation("companion");

    /** {@code requires_core} is not a valid specifier set (S2). */
    public static final ReasonCode REQUIRES_CORE_INVALID = violation("requires_core_invalid");

    /** {@code pack_version} is not a PEP 440 version (S2). */
    public static final ReasonCode PACK_VERSION_INVALID = violation("pack_version_invalid");

    /** A {@code depends_on[].range} is not a valid specifier set (S2). */
    public static final ReasonCode DEPENDENCY_RANGE_INVALID = violation("dependency_range_invalid");

    /** An asset declares a MIME type that is malformed or that its extension does not allow (S2). */
    public static final ReasonCode ASSET_MIME_INVALID = violation("asset_mime_invalid");

    /** A {@code registered.graph.yaml} that no registration wrote. */
    public static final ReasonCode RESERVED_SHARD = violation("reserved_shard");

    /** The manifest lists files the tree does not hold, or misses some (#110). */
    public static final ReasonCode MANIFEST_MEMBERSHIP = violation("manifest_membership");

    /**
     * A symbolic link below the pack root (D1, D6, #597): never read through, and refused on every surface. The pack root
     * itself may be a link (#174).
     */
    public static final ReasonCode PACK_ENTRY_LINKED = violation("pack_entry_linked");

    /** A file of the tree that is not held, excluded or unmodelled (Q1): a warning, never a violation. */
    public static final ReasonCode FILE_NOT_HELD =
            new ReasonCode(Diagnostic.Code.FILE_NOT_HELD.token(), Severity.WARNING);

    /**
     * The code the read states a descriptor format fault under (S2, FR-017): one per {@link DescriptorChecks.Check}, so
     * a check added there needs its code here.
     *
     * @param check which value of the descriptor is malformed
     * @return its code
     */
    public static ReasonCode precheck(DescriptorChecks.Check check) {
        return switch (check) {
            case REQUIRES_CORE -> REQUIRES_CORE_INVALID;
            case PACK_VERSION -> PACK_VERSION_INVALID;
            case DEPENDENCY_RANGE -> DEPENDENCY_RANGE_INVALID;
        };
    }

    /** Every code this class builds, in this class's order. */
    public static final List<ReasonCode> ORDERED = List.of(
            PACK_UNREADABLE,
            ASSEMBLY_REFUSED,
            PACK_ID_INVALID,
            UNREADABLE,
            RETIRED,
            CASE_VARIANT,
            COMPANION,
            REQUIRES_CORE_INVALID,
            PACK_VERSION_INVALID,
            DEPENDENCY_RANGE_INVALID,
            ASSET_MIME_INVALID,
            RESERVED_SHARD,
            MANIFEST_MEMBERSHIP,
            PACK_ENTRY_LINKED,
            FILE_NOT_HELD);

    /** Every code this class builds. */
    public static final Set<ReasonCode> ALL = Set.copyOf(ORDERED);

    private StorageCodes() {}

    private static ReasonCode violation(String token) {
        return new ReasonCode(token, Severity.VIOLATION);
    }
}
