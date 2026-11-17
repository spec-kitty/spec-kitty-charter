package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.rules.Findings;
import java.util.List;

/**
 * The storage rule of the files a pack tree holds and does not hold (#554, S3, Q1, T038): each file the census does not
 * hold is warned about, then ignored. It reads the census of the one read ({@link TreeCensus}); it derives nothing.
 */
public final class NotHeldRules {

    /**
     * One {@link StorageCodes#FILE_NOT_HELD} warning per file the census does not hold, in the census's path order, at
     * the file, as {@code <file>: not held (<reason>)}: an excluded file, a file no pack file kind models and a mapped
     * file nothing names alike, since what counts is that the file is not held, not why.
     */
    public static final Rule<TreeRead> NOT_HELD = NotHeldRules::notHeld;

    /** Every not-held rule, in this class's order. */
    public static final ModularRuleset<TreeRead> RULES =
            ModularRuleset.<TreeRead>emptyRules().must(NOT_HELD);

    private NotHeldRules() {}

    static List<Reason> notHeld(TreeRead read) {
        return read.census().notHeld().stream().map(NotHeldRules::warning).toList();
    }

    /**
     * The warning of one file not held.
     *
     * @param file the file
     * @return the {@link StorageCodes#FILE_NOT_HELD} warning at it
     */
    static Reason warning(NotHeldFile file) {
        return Findings.at(StorageCodes.FILE_NOT_HELD, file.path(), List.of(), file.detail());
    }
}
