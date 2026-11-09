package ai.speckitty.charter.kernel.domain;

/**
 * Refusal codes; part of the contract ({@code CharterRefusalCode}). The constant names are Java's; the
 * {@link #token()} is the snake_case wire spelling, which never changes once released.
 */
public enum RefusalCode {
    /** The asked mission type is not a node of the merged graph. */
    MISSION_TYPE_NOT_FOUND("mission_type_not_found"),
    /** The asked action is not a node of the merged graph. */
    ACTION_NOT_FOUND("action_not_found"),
    /** A pack declares a level other than the one it is configured at. */
    PACK_LEVEL_MISMATCH("pack_level_mismatch"),
    /** A pack's files cannot be read or parsed. */
    PACK_UNREADABLE("pack_unreadable"),
    /** A graph document breaks the DRG schema, for example with an unknown key or relation. */
    DRG_SCHEMA_ERROR("drg_schema_error"),
    /**
     * An edge endpoint is neither a URN nor a bare id its fragment declares. No longer raised: an endpoint is a URN,
     * and one that is not is {@link #DRG_SCHEMA_ERROR}. The code stays so that the contract keeps accepting it.
     */
    UNRESOLVED_EDGE_ENDPOINT("unresolved_edge_endpoint"),
    /**
     * The service failed in a way no other code names. No load or query raises it: the inbound adapter refuses
     * an unexpected exception with it, so that every 500 the contract answers is a refusal.
     */
    INTERNAL_ERROR("internal_error"),
    /**
     * Reserved (ADR-0009): a lower-tier pack breaks the mutability policy of the pack that owns an artifact. No load
     * or query raises it yet; the code is in the contract so that the policy work adds no breaking change.
     */
    GOVERNANCE_POLICY_VIOLATION("governance_policy_violation"),
    /**
     * The pack or configuration files changed while the read was answered, so the answer and the fingerprint it
     * would report might disagree; the read may be retried. Raised by the service info and the tactic reads.
     */
    STALE_INPUT("stale_input"),
    /** The asked pack is not one the service has, or its files cannot be read as a pack; the caller asked wrongly. */
    PACK_NOT_FOUND("pack_not_found"),
    /** The asked pack has no tactic with the asked identity. */
    TACTIC_NOT_FOUND("tactic_not_found"),
    /** The asked pack has no directive with the asked identity. Added for the directive read path (WP05). */
    DIRECTIVE_NOT_FOUND("directive_not_found"),
    /** The asked pack has no styleguide with the asked identity. Added for the styleguide read path (WP07). */
    STYLEGUIDE_NOT_FOUND("styleguide_not_found"),
    /** The asked pack has no paradigm with the asked identity. Added for the paradigm read path (WP08). */
    PARADIGM_NOT_FOUND("paradigm_not_found"),
    /** The asked pack has no toolguide with the asked identity. Added for the toolguide read path (WP09). */
    TOOLGUIDE_NOT_FOUND("toolguide_not_found"),
    /**
     * The path names an artifact kind this service does not serve: any kind token but the served {@code tactic},
     * {@code directive}, {@code styleguide}, {@code paradigm} and {@code toolguide} of the generic artifact item read.
     * Answered as 404. The pack is judged first, so an unknown pack with an unknown kind is {@link #PACK_NOT_FOUND}.
     */
    KIND_NOT_FOUND("kind_not_found"),
    /**
     * A registration request is well-formed but its content breaks a pack invariant or a schema (ADR-0026): the
     * mutated aggregate does not revalidate. Answered as 422. Added for the registration write path (WP05+).
     */
    VALIDATION_FAILED("validation_failed"),
    /**
     * A registration request conflicts with the pack's current state: a net-new id already exists, or a relationship
     * names a {@code from} the pack does not hold (ADR-0026). Answered as 409. Added for the registration write path
     * (WP05+).
     */
    REGISTRATION_CONFLICT("registration_conflict"),
    /**
     * The activation does not exist, or the client may not see it (ADR-0020 D9, D12). Answered as 404. Reserved for the
     * activation set (#125): no operation of this version raises it; the activation operations answer 501 until built.
     */
    ACTIVATION_NOT_FOUND("activation_not_found"),
    /** The activation's offering has no such artifact (ADR-0020 D9). Answered as 404. Reserved (#125). */
    ARTIFACT_NOT_FOUND("artifact_not_found"),
    /** The packs of the activation offer no such preset (ADR-0020 D9). Answered as 404. Reserved (#125). */
    PRESET_NOT_FOUND("preset_not_found"),
    /**
     * A deactivation of an artifact a pack requires (ADR-0012 D7, ADR-0031); the refusal names every requiring pack.
     * Answered as 409. Reserved (#125).
     */
    ARTIFACT_REQUIRED("artifact_required"),
    /**
     * A deactivation of an artifact a pack locks (ADR-0012); the refusal names every locking pack. Answered as 409.
     * Reserved (#125).
     */
    ARTIFACT_LOCKED("artifact_locked"),
    /**
     * A well-formed activation document or body the service refuses: it fails the schema, or names an unknown pack,
     * kind, artifact or mission type (ADR-0020 D9); the refusal lists each refused part. Answered as 422. Reserved
     * (#125).
     */
    ACTIVATION_INVALID("activation_invalid"),

    /** A dependency edge closes a cycle, or a pack depends on itself. */
    PACK_DEPENDENCY_CYCLE("pack_dependency_cycle"),
    /** A dependency names a pack id no configured pack declares. */
    PACK_DEPENDENCY_UNKNOWN("pack_dependency_unknown"),
    /** The held version fits no declared range for that pack. */
    PACK_DEPENDENCY_OUT_OF_RANGE("pack_dependency_out_of_range"),
    /** A {@code parent_pack} names an id no configured pack declares. */
    PACK_PARENT_UNRESOLVED("pack_parent_unresolved"),
    /** A pack depends across a level boundary it may not cross. */
    PACK_LEVEL_VIOLATION("pack_level_violation"),
    /** Two packs in a compose declare the same name. */
    PACK_NAME_CONFLICT("pack_name_conflict"),
    /** Two configured packs declare the same {@code pack_id}. */
    PACK_DUPLICATE_ID("pack_duplicate_id"),
    /** Two sibling packs change or define one artifact and no pack of the compose declares an {@code overrides} edge settling the sibling conflict. */
    PACK_SIBLING_CONFLICT("pack_sibling_conflict"),
    /** A reference that resolves only in another pack is written bare rather than pack-qualified. */
    PACK_REFERENCE_UNQUALIFIED("pack_reference_unqualified"),
    /** A pack-qualified reference names a pack outside the referring pack's {@code depends_on} closure. */
    PACK_REFERENCE_PACK_MISSING("pack_reference_pack_missing"),
    /** A pack-qualified reference names a pack of the closure that does not hold the artifact. */
    PACK_REFERENCE_ARTIFACT_MISSING("pack_reference_artifact_missing"),

    /**
     * A registration of the same {@code pack_version} with another revision than the one the version history holds
     * (ADR-0065 D2, D9). Answered as 409.
     */
    PACK_VERSION_CONFLICT("pack_version_conflict"),
    /**
     * Another artifact or pack still refers to the artifact a {@code DELETE} or a whole-pack replace removes; the
     * refusal names the referring packs and artifacts (ADR-0065 D4, FR-036). Answered as 409.
     */
    ARTIFACT_REFERENCED("artifact_referenced"),
    /**
     * No graph document of the pack holds an edge on the pair a relationship {@code DELETE} names, and no derived
     * relation joins it (ADR-0065 D4). Answered as 404.
     */
    RELATIONSHIP_NOT_FOUND("relationship_not_found"),
    /**
     * The pair a relationship {@code DELETE} names is held outside {@code registered.graph.yaml}, authored in a source
     * shard or {@code drg/fragment.yaml} or derived, or in both kinds of document: a {@code DELETE} removes only a
     * registered edge and never rewrites an author file (ADR-0065 D4, decision DM-01M6TQ62HXYF362GAFS2BZ3PGF). Answered
     * as 409.
     */
    RELATIONSHIP_NOT_REGISTERED("relationship_not_registered"),
    /** No registration is held under the id of a status read (ADR-0065 D8). Answered as 404. */
    REGISTRATION_NOT_FOUND("registration_not_found"),
    /**
     * A source the service refuses: a host off the allowlist, an address that is not global, a bad ref, the wrong media
     * type, a digest or commit mismatch, or a descriptor whose name is not the target (ADR-0065 D10). Answered as 422,
     * in a refused registration status.
     */
    PACK_SOURCE_REFUSED("pack_source_refused"),
    /**
     * Registration or the source kind is switched off, or the system git is missing or older than 2.50.1 (ADR-0065
     * D10, NFR-011). Answered as 503.
     */
    PACK_SOURCE_UNAVAILABLE("pack_source_unavailable"),
    /**
     * A registration or {@code DELETE} would leave an {@code overrides} edge onto a target that is not open (IC-15).
     * Answered as 422, naming the packs.
     */
    OVERRIDE_NOT_PERMITTED("override_not_permitted"),
    /**
     * A registration or {@code DELETE} would leave an {@code enhances} cycle, or an enhancer with no route to a
     * non-enhancer (IC-15). Answered as 422, naming the packs.
     */
    ENHANCES_CYCLE("enhances_cycle"),
    /**
     * A registration or {@code DELETE} would leave an artifact that both enhances and overrides one target (IC-15).
     * Answered as 422, naming the packs.
     */
    CONFLICTING_RELATIONS("conflicting_relations"),
    /**
     * A registration or {@code DELETE} would leave an {@code overrides} edge that resolves to its own source (IC-15).
     * Answered as 422, naming the packs.
     */
    SELF_OVERRIDE("self_override"),
    /**
     * A registration or {@code DELETE} would leave a cycle of {@code overrides} edges (IC-15). Answered as 422, naming
     * the packs.
     */
    OVERRIDE_CYCLE("override_cycle"),
    /**
     * A registration job ended at the source deadline plus 30 seconds (ADR-0065 D8, NFR-008). Answered as 504, in a
     * refused registration status.
     */
    REGISTRATION_TIMED_OUT("registration_timed_out"),
    /**
     * Less than the per-job peak, about three times the archive bound, is free where a registration stages and stores its
     * pack (NFR-009). Answered as 503: synchronously for an upload whose declared length the volumes cannot hold, else in a
     * refused registration status.
     */
    PACK_STORAGE_FULL("pack_storage_full"),

    /** A base prompt's slot declaration is malformed, repeated, nested or unpaired (ADR-0066; reserved for #424). */
    PROMPT_SLOT_MALFORMED("prompt_slot_malformed"),
    /** A slot is not empty at descriptor schema_version 1.0 (ADR-0066; reserved for #424). */
    PROMPT_SLOT_NOT_EMPTY("prompt_slot_not_empty"),
    /** An enhancement or Markdown prompt fragment breaks its load-time rules (ADR-0066; reserved for #424). */
    PROMPT_FRAGMENT_INVALID("prompt_fragment_invalid"),
    /** The prompt in force declares no slot an enhancement names (ADR-0066; reserved for #424). */
    PROMPT_SLOT_MISSING("prompt_slot_missing"),

    /**
     * The asked pack has no mission step contract with the asked identity: the not-found code of kind
     * {@code mission_step_contract}. Answered as 404. Reserved for #99 until its read is built.
     */
    MISSION_STEP_CONTRACT_NOT_FOUND("mission_step_contract_not_found"),
    /**
     * The skeleton name does not decode to a skeleton of the asked mission type (RFC 6901 escaping, D9). Answered as
     * 404. Reserved for #99 until its read is built.
     */
    SKELETON_NOT_FOUND("skeleton_not_found");

    private final String token;

    RefusalCode(String token) {
        this.token = token;
    }

    /**
     * The wire spelling of the code.
     *
     * @return the snake_case token, for example {@code action_not_found}
     */
    public String token() {
        return token;
    }

    /**
     * Whether the refusal is about what was asked rather than about the offering.
     *
     * @return {@code true} for {@link #MISSION_TYPE_NOT_FOUND}, {@link #ACTION_NOT_FOUND}, {@link #PACK_NOT_FOUND},
     *     {@link #TACTIC_NOT_FOUND}, {@link #DIRECTIVE_NOT_FOUND}, {@link #STYLEGUIDE_NOT_FOUND},
     *     {@link #PARADIGM_NOT_FOUND}, {@link #TOOLGUIDE_NOT_FOUND}, {@link #KIND_NOT_FOUND},
     *     {@link #ACTIVATION_NOT_FOUND}, {@link #ARTIFACT_NOT_FOUND}, {@link #PRESET_NOT_FOUND},
     *     {@link #RELATIONSHIP_NOT_FOUND}, {@link #REGISTRATION_NOT_FOUND}, {@link #MISSION_STEP_CONTRACT_NOT_FOUND} and
     *     {@link #SKELETON_NOT_FOUND}
     */
    public boolean notFound() {
        return this == MISSION_TYPE_NOT_FOUND
                || this == ACTION_NOT_FOUND
                || this == PACK_NOT_FOUND
                || this == TACTIC_NOT_FOUND
                || this == DIRECTIVE_NOT_FOUND
                || this == STYLEGUIDE_NOT_FOUND
                || this == PARADIGM_NOT_FOUND
                || this == TOOLGUIDE_NOT_FOUND
                || this == KIND_NOT_FOUND
                || this == ACTIVATION_NOT_FOUND
                || this == ARTIFACT_NOT_FOUND
                || this == PRESET_NOT_FOUND
                || this == RELATIONSHIP_NOT_FOUND
                || this == REGISTRATION_NOT_FOUND
                || this == MISSION_STEP_CONTRACT_NOT_FOUND
                || this == SKELETON_NOT_FOUND;
    }
}
