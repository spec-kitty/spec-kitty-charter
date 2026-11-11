package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How an asset runs (ADR-0054 decision 5): a runtime from the closed allowlist, the entry the runtime is handed, and the
 * arguments after it. The client spawns {@code <runtime's executable> entry args...} without a shell, so the entry is
 * exactly one argv element and no field is a command string. The schema checks the same shape; this type keeps the
 * domain from holding anything else.
 *
 * @param runtime the runtime, from the allowlist
 * @param minVersion the lowest runtime version the asset needs, as dotted numbers, when stated
 * @param entry a payload of the same pack, relative to its {@code assets/} directory: the asset's own or another's
 *     (the pack store checks that the pack holds it, ADR-0054 decision 5 as amended on 2026-11-03)
 * @param args the arguments after the entry, in order
 */
public record Executable(ExecutableRuntime runtime, Optional<String> minVersion, String entry, List<String> args) {

    private static final Pattern VERSION = Pattern.compile("[0-9]+(\\.[0-9]+){0,2}");

    private static final ModularRuleset<Executable> RULES = ModularRuleset.<Executable>emptyRules()
            .must(Constraints.of(
                    "An executable's min_version is dotted numbers.",
                    e -> e.minVersion.map(v -> VERSION.matcher(v).matches()).orElse(true)))
            .must(Constraints.of(
                    "An executable's entry is a relative path inside the pack's assets: no absolute path, no .. or"
                            + " hidden segment, no whitespace, at most 255 characters.",
                    e -> IdentifierGrammar.isRelativePath(e.entry)))
            .must(Constraints.of(
                    "An executable's entry is exactly one argv element: its args do not repeat it.",
                    e -> !e.args.contains(e.entry)))
            .must(Constraints.of(
                    "An executable's args are not blank.", e -> e.args.stream().noneMatch(String::isBlank)));

    /**
     * Checks the block and copies the arguments.
     *
     * @param runtime the runtime
     * @param minVersion the minimum version, possibly empty
     * @param entry the entry
     * @param args the arguments
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    public Executable(ExecutableRuntime runtime, Optional<String> minVersion, String entry, List<String> args) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.minVersion = Objects.requireNonNull(minVersion, "minVersion");
        this.entry = Objects.requireNonNull(entry, "entry");
        this.args = List.copyOf(args);
        RULES.guard(this);
    }

    /**
     * The argv after the runtime's own executable: the entry, then the arguments.
     *
     * @return an immutable list whose first element is the entry
     */
    public List<String> argv() {
        List<String> argv = new ArrayList<>(args.size() + 1);
        argv.add(entry);
        argv.addAll(args);
        return List.copyOf(argv);
    }
}
