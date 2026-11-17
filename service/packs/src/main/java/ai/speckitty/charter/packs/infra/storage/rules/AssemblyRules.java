package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.util.List;

/**
 * The storage rule of the pack as a whole (#554, S1, A-M1, T038): the store cannot hold a pack its read refused whole.
 * The read records that refusal as a fact of the one read ({@link Assembly.Refused}); this rule states it, once, where
 * the reader's own faults do not already: a pack with no {@code pack.yaml} (R2 row 24), and an aggregate invariant the
 * readable artifacts break (row 26). A descriptor the reader could not read is the reader's fault, and a root it could not
 * read too.
 */
public final class AssemblyRules {

    /** Where the store's refusal of the pack is placed when it is not the descriptor's: the pack as a whole. */
    static final String PACK = "pack";

    /** Where the store's refusal of the descriptor is placed. */
    static final String DESCRIPTOR = "pack.yaml";

    /**
     * One violation when the store refused the pack as a whole and no read fault states it: {@code pack_unreadable} at
     * {@value #DESCRIPTOR} for the descriptor's refusal, else at {@value #PACK}, the aggregate's own refusal as
     * {@link StorageCodes#ASSEMBLY_REFUSED}; in the store's words. A registration lists it right after the graph faults.
     */
    public static final Rule<TreeRead> REFUSED = AssemblyRules::refused;

    /** Every assembly rule, in this class's order. */
    public static final ModularRuleset<TreeRead> RULES =
            ModularRuleset.<TreeRead>emptyRules().must(REFUSED);

    private AssemblyRules() {}

    static List<Reason> refused(TreeRead read) {
        if (!(read.contents().assembly() instanceof Assembly.Refused refused)
                || (refused.atDescriptor()
                        && read.contents().descriptor() instanceof FileRead.Unreadable<PackDescriptor>)) {
            return List.of();
        }
        ReasonCode code = refused.code() == RefusalCode.PACK_UNREADABLE
                ? StorageCodes.PACK_UNREADABLE
                : StorageCodes.ASSEMBLY_REFUSED;
        Reason reason = new Reason(
                code.value(),
                Evaluation.FAIL,
                code,
                Location.in(refused.atDescriptor() ? DESCRIPTOR : PACK),
                refused.message());
        return read.readFaults().contains(reason) ? List.of() : List.of(reason);
    }

    /**
     * Whether a reason is one {@link #REFUSED} states.
     *
     * @param reason a reason
     * @return true for the store's refusal of the pack as a whole
     */
    static boolean statesRefusal(Reason reason) {
        String file = reason.location().file();
        return (StorageCodes.PACK_UNREADABLE.equals(reason.code())
                        || StorageCodes.ASSEMBLY_REFUSED.equals(reason.code()))
                && (PACK.equals(file) || DESCRIPTOR.equals(file));
    }
}
