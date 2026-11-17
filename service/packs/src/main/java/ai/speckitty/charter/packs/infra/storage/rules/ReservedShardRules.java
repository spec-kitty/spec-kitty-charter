package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import java.util.List;

/**
 * The storage rule of the reserved shard (#554, S3, C-4, T038): the registered relationships' shard is the registration
 * store's own, so a regular file at its path that no registration wrote is refused. It reads the census's stamp fact,
 * never an unreadable graph: the read reads the file as any root shard, as the store does.
 */
public final class ReservedShardRules {

    /** The pack-relative path registered relationships persist to: a root shard the fingerprint already covers. */
    public static final String SHARD = "registered.graph.yaml";

    /**
     * Why a {@value #SHARD} no registration wrote is refused ({@code reserved_shard}), the one wording of validate mode
     * and the registration (#554, R2 row 28).
     */
    public static final String RESERVED = SHARD
            + " is reserved for the registered relationships; a file of this name without generated_by: "
            + "charter-registration is refused. Register the edges in a shard of your own.";

    /**
     * One {@link StorageCodes#RESERVED_SHARD} at {@value #SHARD} when a regular file there carries no registration
     * stamp ({@link TreeCensus.ReservedShard#UNSTAMPED}); a registration lists it after the pack's other faults.
     */
    public static final Rule<TreeRead> UNSTAMPED = ReservedShardRules::unstamped;

    /** Every reserved-shard rule, in this class's order. */
    public static final ModularRuleset<TreeRead> RULES =
            ModularRuleset.<TreeRead>emptyRules().must(UNSTAMPED);

    private ReservedShardRules() {}

    static List<Reason> unstamped(TreeRead read) {
        if (read.census().reservedShard() != TreeCensus.ReservedShard.UNSTAMPED) {
            return List.of();
        }
        return List.of(new Reason(
                StorageCodes.RESERVED_SHARD.value(),
                Evaluation.FAIL,
                StorageCodes.RESERVED_SHARD,
                Location.in(SHARD),
                RESERVED));
    }
}
