package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.deployable.bdd.ContractConformance.Operation;
import io.cucumber.java.AfterAll;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The switch gate of ADR-0059 D1: an operation is ready for core to switch to it when its design-derived
 * {@code @contract} scenarios and its contract tests pass against the service. The register,
 * {@code contracts/charter/scenarios/switch-gate.yaml}, says per operation whether it is {@code ready} or
 * {@code held}; this check holds the register to what the suite exercised.
 *
 * <p>An operation passes when every status it declares by number (not {@code default}) was exercised on it by a
 * scenario. A success answer (2xx) counts from any exchange, setup included, as the R-20 check always counted it. Any
 * other answer counts only when a step asserted its status ({@code Then the response status is <n>}), so an incidental
 * refusal of a setup request does not cover the operation. After the last scenario the gate fails when:
 *
 * <ul>
 *   <li>a success answer of a built operation was exercised by no scenario (R-20), whatever the register says;
 *   <li>the register lacks an operation of the contract, or names one the contract does not declare (fail closed);
 *   <li>a {@code ready} operation has a declared status no scenario exercised on it;
 *   <li>a {@code held} operation that is not reserved would pass as {@code ready}, so the register cannot go stale;
 *   <li>a reserved operation ({@code x-reserved}, a 501 pin) is {@code ready}.
 * </ul>
 *
 * <p>The contract tests are the checks every exchange already passes (its schema, links and fingerprint), so an
 * exercised answer is also a checked one. Whatever the outcome, the per-operation report is written to
 * {@code service/deployable/build/reports/switch-gate/switch-gate.md}, a declared output of the test task. A run that selects only some scenarios reports the others'
 * answers as gaps.
 */
public final class SwitchGate {

    private static final String REGISTER = "scenarios/switch-gate.yaml";
    private static final String SCENARIOS_PREFIX = "classpath:scenarios/";
    private static final Path REPORT = Path.of(
                    System.getProperty("charter.switchGateReport", "build/reports/switch-gate/switch-gate.md"))
            .toAbsolutePath();
    private static final Set<Exercise> EXERCISED = ConcurrentHashMap.newKeySet();

    private SwitchGate() {}

    /**
     * One exchange as the scenario saw it.
     *
     * @param method the lower-case method
     * @param path the path below the server prefix, without its query
     * @param status the response status
     */
    record Exchange(String method, String path, int status) {}

    /**
     * One answer a scenario exercised, counted toward its operation.
     *
     * @param method the lower-case method
     * @param template the path template of the operation that answered
     * @param status the response status
     * @param scenario the scenario, as {@code <feature file below scenarios/>:<line>}
     */
    record Exercise(String method, String template, int status, String scenario) {}

    /**
     * The register's verdict on one operation.
     *
     * @param ready true for {@code switch: ready}, false for {@code switch: held}
     * @param reason why a held operation is held; empty for a ready one
     */
    record Entry(boolean ready, String reason) {}

    /**
     * Records one exchange of a scenario.
     *
     * @param contract the contract
     * @param exchange the exchange
     * @param asserted whether a step asserted its status
     * @param scenarioUri the scenario's feature URI, for example {@code classpath:scenarios/security.feature}
     * @param line the scenario's line
     */
    static void record(
            ContractConformance contract, Exchange exchange, boolean asserted, String scenarioUri, int line) {
        exercised(contract, exchange, asserted, scenario(scenarioUri, line)).ifPresent(EXERCISED::add);
    }

    /**
     * The answer an exchange exercises.
     *
     * @param contract the contract
     * @param exchange the exchange
     * @param asserted whether a step asserted its status
     * @param scenario the scenario, as {@code file:line}
     * @return the exercise; empty when no operation defines the method on the path, or when the answer is not a
     *     success and no step asserted its status
     */
    static Optional<Exercise> exercised(
            ContractConformance contract, Exchange exchange, boolean asserted, String scenario) {
        if (!asserted && !isSuccess(exchange.status())) {
            return Optional.empty();
        }
        return contract.operationTemplate(exchange.method(), exchange.path())
                .map(template -> new Exercise(exchange.method(), template, exchange.status(), scenario));
    }

    private static String scenario(String uri, int line) {
        String file = uri.startsWith(SCENARIOS_PREFIX) ? uri.substring(SCENARIOS_PREFIX.length()) : uri;
        return file + ":" + line;
    }

    /**
     * Reads a register.
     *
     * @param yaml the register's text
     * @return the entries, by {@code operationId}
     * @throws IllegalArgumentException when the register or an entry is malformed
     */
    static Map<String, Entry> register(String yaml) {
        Map<?, ?> root = map(new Load(LoadSettings.builder().build()).loadFromString(yaml), "the register");
        if (!root.keySet().equals(Set.of("operations"))) {
            throw new IllegalArgumentException(
                    "the register holds exactly one key, operations; found " + root.keySet());
        }
        Map<String, Entry> operations = new TreeMap<>();
        map(root.get("operations"), "operations")
                .forEach((id, value) ->
                        operations.put(String.valueOf(id), entry(String.valueOf(id), map(value, "operation " + id))));
        return operations;
    }

    private static Entry entry(String id, Map<?, ?> fields) {
        Object verdict = fields.get("switch");
        Object reason = fields.get("reason");
        if ("ready".equals(verdict) && fields.keySet().equals(Set.of("switch"))) {
            return new Entry(true, "");
        }
        if ("held".equals(verdict)
                && fields.keySet().equals(Set.of("switch", "reason"))
                && reason instanceof String text
                && !text.isBlank()) {
            return new Entry(false, text);
        }
        throw new IllegalArgumentException(
                "operation " + id + ": an entry is {switch: ready} or {switch: held, reason: <why>}; found " + fields);
    }

    private static Map<?, ?> map(@Nullable Object value, String what) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException(what + " must be a mapping");
    }

    /**
     * The declared statuses of an operation that no scenario exercised on it.
     *
     * @param operation the operation
     * @param exercised the statuses the scenarios exercised on it
     * @return the gaps, sorted
     */
    static List<Integer> gaps(Operation operation, Set<Integer> exercised) {
        return operation.statuses().stream()
                .filter(status -> !exercised.contains(status))
                .sorted()
                .toList();
    }

    /**
     * Every way the register and the run disagree, in a stable order; empty when the gate passes.
     *
     * @param operations the contract's operations, keyed by operation id
     * @param exercised what the scenarios exercised
     * @param register the register's entries, keyed by operation id
     * @return one line per violation
     */
    static List<String> violations(
            Map<String, Operation> operations, Set<Exercise> exercised, Map<String, Entry> register) {
        Map<String, Set<Integer>> statuses = statusesByOperation(operations, exercised);
        List<String> violations = new ArrayList<>(successGaps(operations, statuses));
        violations.addAll(membership(operations, register));
        for (Operation operation : sorted(operations)) {
            Entry entry = register.get(operation.operationId());
            if (entry != null) {
                verdict(operation, entry, statuses.get(operation.operationId())).ifPresent(violations::add);
            }
        }
        return violations;
    }

    private static List<String> successGaps(Map<String, Operation> operations, Map<String, Set<Integer>> statuses) {
        List<String> gaps = new ArrayList<>();
        for (Operation operation : sorted(operations)) {
            if (operation.reserved()) {
                continue;
            }
            operation.statuses().stream()
                    .filter(SwitchGate::isSuccess)
                    .filter(status -> !statuses.get(operation.operationId()).contains(status))
                    .sorted()
                    .forEach(status -> gaps.add("No scenario exercises this success answer of the contract (R-20): "
                            + signature(operation) + " " + status));
        }
        return gaps;
    }

    private static List<String> membership(Map<String, Operation> operations, Map<String, Entry> register) {
        List<String> violations = new ArrayList<>();
        new TreeSet<>(operations.keySet())
                .stream()
                        .filter(id -> !register.containsKey(id))
                        .forEach(id ->
                                violations.add(id + ": the contract declares it and the register has no entry for it;"
                                        + " an operation the gate has no verdict for is not switched"));
        register.keySet().stream()
                .filter(id -> !operations.containsKey(id))
                .forEach(id -> violations.add(
                        id + ": the register names it and the contract declares no such" + " operation"));
        return violations;
    }

    private static Optional<String> verdict(Operation operation, Entry entry, Set<Integer> exercised) {
        List<Integer> gaps = gaps(operation, exercised);
        String id = operation.operationId();
        if (entry.ready() && operation.reserved()) {
            return Optional.of(id + ": is ready, but it is reserved (x-reserved): a 501 pin does not count toward"
                    + " the gate (ADR-0059 D1)");
        }
        if (entry.ready() && !gaps.isEmpty()) {
            return Optional.of(id + ": is ready, but no scenario asserts its declared " + statuses(gaps) + " on "
                    + signature(operation) + "; add a scenario, or hold it with a reason");
        }
        if (!entry.ready() && !operation.reserved() && gaps.isEmpty()) {
            return Optional.of(id + ": is held (" + entry.reason() + "), but every declared answer is exercised;"
                    + " mark it ready");
        }
        return Optional.empty();
    }

    private static Map<String, Set<Integer>> statusesByOperation(
            Map<String, Operation> operations, Set<Exercise> exercised) {
        Map<String, Set<Integer>> statuses = new LinkedHashMap<>();
        for (Operation operation : operations.values()) {
            statuses.put(
                    operation.operationId(),
                    exercised.stream()
                            .filter(exercise -> answers(operation, exercise))
                            .map(Exercise::status)
                            .collect(Collectors.toSet()));
        }
        return statuses;
    }

    private static boolean answers(Operation operation, Exercise exercise) {
        return operation.method().equals(exercise.method())
                && operation.template().equals(exercise.template());
    }

    /**
     * The per-operation report: each operation's verdict and, per declared status, the scenarios that exercised it.
     *
     * @param operations the contract's operations
     * @param exercised what the scenarios exercised
     * @param register the register's entries
     * @return the report, as Markdown
     */
    static String report(Map<String, Operation> operations, Set<Exercise> exercised, Map<String, Entry> register) {
        StringBuilder report = new StringBuilder("# Switch gate (ADR-0059 D1)\n\n");
        report.append("Generated by the contract suite from `scenarios/switch-gate.yaml` and the exchanges it made.\n");
        for (Operation operation : sorted(operations)) {
            Entry entry = register.get(operation.operationId());
            report.append("\n## ")
                    .append(operation.operationId())
                    .append(" — ")
                    .append(entry == null ? "no entry" : entry.ready() ? "ready" : "held: " + entry.reason())
                    .append("\n\n`")
                    .append(signature(operation))
                    .append("`\n\n| Status | Scenarios |\n| --- | --- |\n");
            for (int status : new TreeSet<>(operation.statuses())) {
                report.append("| ")
                        .append(status)
                        .append(" | ")
                        .append(scenarios(operation, status, exercised))
                        .append(" |\n");
            }
        }
        return report.toString();
    }

    private static String scenarios(Operation operation, int status, Set<Exercise> exercised) {
        Set<String> scenarios = exercised.stream()
                .filter(exercise -> answers(operation, exercise) && exercise.status() == status)
                .map(Exercise::scenario)
                .collect(Collectors.toCollection(TreeSet::new));
        return scenarios.isEmpty() ? "**none**" : String.join(", ", scenarios);
    }

    /** Fails the suite when the register and the scenarios disagree, after writing the per-operation report. */
    @AfterAll
    public static void everyOperationPassesTheSwitchGate() {
        Map<String, Operation> operations = new ContractConformance().operations();
        Map<String, Entry> register = register(readRegister());
        Set<Exercise> exercised = Set.copyOf(EXERCISED);
        EXERCISED.clear();
        writeReport(report(operations, exercised, register));
        List<String> violations = violations(operations, exercised, register);
        if (!violations.isEmpty()) {
            throw new AssertionError("The switch gate (ADR-0059 D1, " + REGISTER + ") fails; the report is " + REPORT
                    + ":\n  " + String.join("\n  ", violations));
        }
    }

    private static String readRegister() {
        try (InputStream in = SwitchGate.class.getClassLoader().getResourceAsStream(REGISTER)) {
            if (in == null) {
                throw new IllegalStateException(REGISTER + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeReport(String report) {
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Operation> sorted(Map<String, Operation> operations) {
        return operations.values().stream()
                .sorted(Comparator.comparing(Operation::operationId))
                .toList();
    }

    private static String signature(Operation operation) {
        return operation.method().toUpperCase(Locale.ROOT) + " " + operation.template();
    }

    private static String statuses(List<Integer> gaps) {
        return gaps.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status <= 299;
    }
}
