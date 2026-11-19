package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.rules.Findings;
import java.util.List;

/**
 * The storage rule of the links in a pack tree (D1, D6, #597): a pack holds no symbolic link below its root, whether it
 * leads to a file, a directory or nothing, inside the pack or out of it. The walk lists a link apart and never reads
 * through it; this rule refuses each one the census lists ({@link TreeCensus.Standing#LINK}), so a registration and
 * validate mode refuse it alike. The pack root itself may be a link (#174): the walk enters it, and it is no entry.
 */
public final class LinkRules {

    /** Why a link in a pack is refused: the one wording of every surface, after the link's path. */
    public static final String LINKED = "is a symbolic link; a pack holds no links";

    /**
     * One {@link StorageCodes#PACK_ENTRY_LINKED} violation per link the census lists, in the census's path order, at the
     * link's pack-relative path.
     */
    public static final Rule<TreeRead> LINKED_ENTRY = LinkRules::linked;

    /** Every link rule, in this class's order. */
    public static final ModularRuleset<TreeRead> RULES =
            ModularRuleset.<TreeRead>emptyRules().must(LINKED_ENTRY);

    private LinkRules() {}

    static List<Reason> linked(TreeRead read) {
        return read.census().entries().stream()
                .filter(entry -> entry.standing() == TreeCensus.Standing.LINK)
                .map(entry -> Findings.at(StorageCodes.PACK_ENTRY_LINKED, entry.path(), List.of(), detail(entry.path())))
                .toList();
    }

    /**
     * The detail of a link's refusal, as every surface words it: {@code <link>: }{@value #LINKED}.
     *
     * @param link the link's pack-relative path
     * @return the detail
     */
    public static String detail(String link) {
        return PackCodes.detail(link, LINKED);
    }
}
