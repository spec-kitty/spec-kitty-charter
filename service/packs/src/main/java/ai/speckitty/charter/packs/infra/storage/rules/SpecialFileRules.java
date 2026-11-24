package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.rules.Findings;
import java.util.List;

/**
 * The storage rule of the special files in a pack tree (operator ruling R-ad): a pack holds only regular files and
 * directories, so an entry that is neither (a FIFO, a socket, a device) is refused, wherever it stands, as a link is
 * ({@link LinkRules}). The walk lists such an entry apart and never opens it, since a read of a FIFO would wait for a
 * writer; this rule refuses each one the census lists ({@link TreeCensus.Standing#SPECIAL}), so a registration, validate
 * mode and remediation's precondition refuse it alike.
 *
 * <p>Its code is {@link StorageCodes#UNREADABLE}, the code of a file the service cannot read as pack content, which
 * already answers something at the manifest's path that is not a regular file ({@link ManifestRules#REGULAR}): no code is
 * added for the same fault.
 */
public final class SpecialFileRules {

    /** Why a special file in a pack is refused: the one wording of every surface, after the file's path. */
    public static final String SPECIAL =
            "is not a regular file or a directory; a pack holds only regular files and directories";

    /**
     * One {@link StorageCodes#UNREADABLE} violation per special file the census lists, in the census's path order, at
     * the file's pack-relative path.
     */
    public static final Rule<TreeRead> SPECIAL_ENTRY = SpecialFileRules::special;

    /** Every special-file rule, in this class's order. */
    public static final ModularRuleset<TreeRead> RULES =
            ModularRuleset.<TreeRead>emptyRules().must(SPECIAL_ENTRY);

    private SpecialFileRules() {}

    static List<Reason> special(TreeRead read) {
        return read.census().entries().stream()
                .filter(entry -> entry.standing() == TreeCensus.Standing.SPECIAL)
                .map(entry -> Findings.at(StorageCodes.UNREADABLE, entry.path(), List.of(), detail(entry.path())))
                .toList();
    }

    /**
     * Whether a reason is one {@link #SPECIAL_ENTRY} states.
     *
     * @param reason a reason
     * @return true for a special file's refusal
     */
    static boolean states(Reason reason) {
        return StorageCodes.UNREADABLE.equals(reason.code())
                && detail(reason.location().file()).equals(reason.detail());
    }

    /**
     * The detail of a special file's refusal, as every surface words it: {@code <file>: }{@value #SPECIAL}.
     *
     * @param file the file's pack-relative path
     * @return the detail
     */
    public static String detail(String file) {
        return PackCodes.detail(file, SPECIAL);
    }
}
