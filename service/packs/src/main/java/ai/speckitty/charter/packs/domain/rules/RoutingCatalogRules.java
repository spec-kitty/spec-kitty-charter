package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.RoutingCatalogFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The content rules of a pack's routing catalogs (ADR-0080 decision 5): what the catalog's schema cannot state. A
 * {@code task_fit} or a tier constraint names a task type the catalog declares; a task type id, a model id, a model's
 * task type among its fits and a task type among the tier constraints each stand once. Each fault is an
 * {@link PackCodes#ARTIFACT_SCHEMA_ERROR}, "a rule of its kind that the schema cannot state", at the catalog's file and
 * the member's place, so the load leaves the catalog out, and a registration and validate mode refuse it. A catalog
 * references nothing outside itself, so the reference closure has no subject for it.
 */
public final class RoutingCatalogRules {

    /** Every inconsistency of every catalog, in file order and, within a catalog, in written order. */
    public static final Rule<PackContents> CONSISTENT = RoutingCatalogRules::inconsistencies;

    /** Every routing catalog rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(CONSISTENT);

    private RoutingCatalogRules() {}

    static List<Reason> inconsistencies(PackContents contents) {
        List<Reason> reasons = new ArrayList<>();
        for (RoutingCatalogFacts catalog : contents.routingCatalogs()) {
            reasons.addAll(judge(contents.root().name(), catalog));
        }
        return reasons;
    }

    /**
     * The faults of one catalog, in written order.
     *
     * @param packName the pack's name, for the detail
     * @param catalog the catalog's facts
     * @return one reason per fault
     */
    static List<Reason> judge(String packName, RoutingCatalogFacts catalog) {
        List<Reason> reasons = new ArrayList<>();
        String origin = Paths.origin(packName, catalog.file()) + ": routing catalog " + catalog.id() + ": ";
        Set<String> declared = new HashSet<>();
        for (int i = 0; i < catalog.taskTypeIds().size(); i++) {
            String id = catalog.taskTypeIds().get(i);
            if (!declared.add(id)) {
                reasons.add(fault(
                        catalog, place("task_types", i, "id"), origin + "the task type " + id + " is declared twice."));
            }
        }
        Set<String> models = new HashSet<>();
        for (int i = 0; i < catalog.models().size(); i++) {
            RoutingCatalogFacts.ModelFacts model = catalog.models().get(i);
            if (!models.add(model.id())) {
                reasons.add(fault(
                        catalog, place("models", i, "id"), origin + "the model " + model.id() + " is declared twice."));
            }
            Set<String> fits = new HashSet<>();
            for (int j = 0; j < model.fitTaskTypes().size(); j++) {
                String taskType = model.fitTaskTypes().get(j);
                List<String> at = List.of("models", String.valueOf(i), "task_fit", String.valueOf(j), "task_type");
                if (!declared.contains(taskType)) {
                    reasons.add(fault(
                            catalog,
                            at,
                            origin + "the model " + model.id() + " has a fit for the task type " + taskType
                                    + ", which the catalog does not declare."));
                } else if (!fits.add(taskType)) {
                    reasons.add(fault(
                            catalog,
                            at,
                            origin + "the model " + model.id() + " has two fits for the task type " + taskType + "."));
                }
            }
        }
        Set<String> constrained = new HashSet<>();
        for (int i = 0; i < catalog.constraintTaskTypes().size(); i++) {
            String taskType = catalog.constraintTaskTypes().get(i);
            List<String> at = List.of("routing_policy", "tier_constraints", String.valueOf(i), "task_type");
            if (!declared.contains(taskType)) {
                reasons.add(fault(
                        catalog,
                        at,
                        origin + "a tier constraint names the task type " + taskType
                                + ", which the catalog does not declare."));
            } else if (!constrained.add(taskType)) {
                reasons.add(fault(catalog, at, origin + "the task type " + taskType + " has two tier constraints."));
            }
        }
        return reasons;
    }

    private static List<String> place(String list, int index, String member) {
        return List.of(list, String.valueOf(index), member);
    }

    private static Reason fault(RoutingCatalogFacts catalog, List<String> place, String detail) {
        return Findings.at(PackCodes.ARTIFACT_SCHEMA_ERROR, catalog.file(), place, detail);
    }
}
