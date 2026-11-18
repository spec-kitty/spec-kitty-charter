package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.ReferenceFacts;
import java.util.ArrayList;
import java.util.List;

/**
 * The closure as a rule of the PACK ruleset (#355): every candidate of the one read that does not close
 * ({@link ReferenceClosure}) is one {@link PackCodes#ARTIFACT_SCHEMA_ERROR} at its file, with the reason the load records when
 * it leaves the artifact out. Validate mode and a registration state it through {@link PackRulesets#content()}; the load,
 * the whole-pack write and a per-file write call {@link ReferenceClosure} itself, so the four paths judge with one rule.
 */
public final class ReferenceRules {

    /** The candidates that do not close, in the order the load meets them. */
    public static final Rule<PackContents> UNCLOSED =
            contents -> stated(contents.assembly().references());

    /** Every closure rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(UNCLOSED);

    private ReferenceRules() {}

    /**
     * The findings of the facts of one read: one {@link PackCodes#ARTIFACT_SCHEMA_ERROR} at each candidate that does not
     * close, in the candidates' order.
     *
     * @param facts the facts
     * @return the reasons; empty when every candidate closes
     */
    static List<Reason> stated(ReferenceFacts facts) {
        ReferenceClosure closure = ReferenceClosure.of(facts);
        List<Reason> found = new ArrayList<>();
        for (ReferenceFacts.Placed placed : facts.candidates()) {
            closure.firstUnresolved(placed.subject())
                    .ifPresent(reason ->
                            found.add(Findings.at(PackCodes.ARTIFACT_SCHEMA_ERROR, placed.file(), List.of(), reason)));
        }
        return found;
    }
}
