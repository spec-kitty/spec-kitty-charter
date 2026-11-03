package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.BuiltinGuard;
import ai.speckitty.charter.packs.domain.Executable;
import ai.speckitty.charter.packs.domain.ExecutableRuntime;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The closed {@code guard} and {@code lifecycle} schemas and the asset's {@code executable} block (ADR-0054 decisions 1
 * and 5, ADR-0050 decision 17): an accepted document validates, and each twin, which differs from it in one line, is
 * refused for that one property.
 */
@DisplayName("Guard, lifecycle and asset executable schemas (ADR-0054, #156)")
class GuardLifecycleSchemaTest {

    private static final String GUARD = """
            schema_version: '1.0'
            id: traceability-ready
            title: Ready
            description: D
            interface: guard/1
            implemented_by: asset:carrier-lint
            config_schema: {type: object}
            limits: {timeout_seconds: 60, network: false}
            on_indeterminate: pass
            """;

    private static final String LIFECYCLE = """
            schema_version: '1.0'
            id: work-package
            title: Work package
            initial: planned
            states:
            - {id: planned, title: Planned}
            - {id: in_progress, title: In progress}
            - {id: done, title: Done, terminal: true}
            transitions:
            - {from: planned, to: in_progress, event: claim}
            - from: in_progress
              to: done
              guard:
              - {ref: subtasks-ready, blocking: false, config: {strict: true}}
            - {from: '*', to: done}
            - {from: [planned, in_progress], to: done, event: abort}
            """;

    private static final String ASSET = """
            id: carrier-lint
            mime: text/x-python
            path: carrier_lint.py
            executable:
              runtime: python
              min_version: '3.11'
              entry: carrier_lint.py
              args: [--guard]
            """;

    private static List<String> facts(String kind, String yaml) {
        return PackSchemas.bundled().validate(kind, "x." + kind + ".yaml", Yaml.load(yaml)).stream()
                .map(v -> v.keyword() + " /" + String.join("/", v.pointer()) + " "
                        + v.property().orElse("-"))
                .sorted()
                .toList();
    }

    private static String twin(String accepted, String line, String replacement) {
        assertThat(accepted).as("the accepted document holds %s", line).contains(line);
        String twin = accepted.replace(line, replacement);
        assertThat(twin.lines().count()).isEqualTo(accepted.lines().count());
        return twin;
    }

    private static Arguments row(String line, String replacement, String expected) {
        return Arguments.of(line, replacement, expected);
    }

    static Stream<Arguments> guardTwins() {
        return Stream.of(
                row("interface: guard/1", "interface: guard/2", "const /interface -"),
                row(
                        "implemented_by: asset:carrier-lint",
                        "implemented_by: builtin:subtasks-complete",
                        "pattern /implemented_by -"),
                row("implemented_by: asset:carrier-lint", "implemented_by: carrier-lint", "pattern /implemented_by -"),
                row("implemented_by: asset:carrier-lint", "implemented_by: Qa::asset:x", "pattern /implemented_by -"),
                row("on_indeterminate: pass", "on_indeterminate: warn", "enum /on_indeterminate -"),
                row("on_indeterminate: pass", "severity: smell", "additionalProperties / severity"),
                row(
                        "limits: {timeout_seconds: 60, network: false}",
                        "limits: {timeout_seconds: 0, network: false}",
                        "minimum /limits/timeout_seconds -"),
                row(
                        "limits: {timeout_seconds: 60, network: false}",
                        "limits: {timeout_seconds: 60, sandbox: false}",
                        "additionalProperties /limits sandbox"),
                row("config_schema: {type: object}", "config_schema: object", "type /config_schema -"),
                row("id: traceability-ready", "id: Traceability", "pattern /id -"),
                // A word a YAML 1.1 reader takes for a boolean or null would be read as one by core (ADR-0036).
                row("id: traceability-ready", "id: 'on'", "not /id -"),
                row(
                        "limits: {timeout_seconds: 60, network: false}",
                        "limits: {timeout_seconds: 4294967297, network: false}",
                        "maximum /limits/timeout_seconds -"));
    }

    static Stream<Arguments> lifecycleTwins() {
        String entry = "  - {ref: subtasks-ready, blocking: false, config: {strict: true}}";
        return Stream.of(
                row("initial: planned", "initial: Planned", "pattern /initial -"),
                row("- {id: planned, title: Planned}", "- {id: planned}", "required /states/0 title"),
                row(
                        "- {id: planned, title: Planned}",
                        "- {id: planned, title: P, on_enter: x}",
                        "additionalProperties /states/0 on_enter"),
                row(
                        "- {from: planned, to: in_progress, event: claim}",
                        "- {from: planned, to: in_progress, event: Claim}",
                        "pattern /transitions/0/event -"),
                row(
                        "- {from: planned, to: in_progress, event: claim}",
                        "- {from: planned, to: '*'}",
                        "pattern /transitions/0/to -"),
                row(
                        "- {from: planned, to: in_progress, event: claim}",
                        "- {from: planned, to: in_progress, gate: g}",
                        "additionalProperties /transitions/0 gate"),
                row("- {from: '*', to: done}", "- {from: [], to: done}", "~minItems /transitions/2/from -"),
                row(
                        entry,
                        "  - {gate: subtasks-ready}",
                        "additionalProperties /transitions/1/guard/0 gate; required /transitions/1/guard/0 ref"),
                row(entry, "  - {ref: 'guard:subtasks-ready'}", "pattern /transitions/1/guard/0/ref -"),
                // A word a YAML 1.1 reader takes for a boolean is refused as a state id or an event (ADR-0036).
                row("initial: planned", "initial: 'no'", "not /initial -"),
                row("id: work-package", "id: 'off'", "not /id -"),
                row(entry, "  - {ref: 'null'}", "not /transitions/1/guard/0/ref -"),
                row(
                        "- {from: planned, to: in_progress, event: claim}",
                        "- {from: planned, to: in_progress, event: 'yes'}",
                        "not /transitions/0/event -"),
                row(
                        "- {from: [planned, in_progress], to: done, event: abort}",
                        "- {from: [planned, planned], to: done, event: abort}",
                        "~uniqueItems /transitions/3/from -"));
    }

    static Stream<Arguments> executableTwins() {
        return Stream.of(
                row("  runtime: python", "  runtime: sh", "enum /executable/runtime -"),
                row("  runtime: python", "  runtime: bash", "enum /executable/runtime -"),
                row("  runtime: python", "  runtime: /usr/bin/python3", "enum /executable/runtime -"),
                row("  entry: carrier_lint.py", "  entry: /opt/carrier_lint.py", "pattern /executable/entry -"),
                row("  entry: carrier_lint.py", "  entry: ../carrier_lint.py", "pattern /executable/entry -"),
                row("  entry: carrier_lint.py", "  entry: python carrier_lint.py", "pattern /executable/entry -"),
                row("  entry: carrier_lint.py", "  entry: 'C:\\carrier_lint.py'", "pattern /executable/entry -"),
                row("  args: [--guard]", "  args: --guard", "type /executable/args -"),
                row(
                        "  args: [--guard]",
                        "  command: python carrier_lint.py --guard",
                        "additionalProperties /executable command"),
                row("  min_version: '3.11'", "  min_version: 3.11+", "pattern /executable/min_version -"));
    }

    @Test
    @DisplayName("an accepted guard, lifecycle and executable asset validate")
    void theAcceptedDocumentsValidate() {
        assertThat(facts("guard", GUARD)).isEmpty();
        assertThat(facts("lifecycle", LIFECYCLE)).isEmpty();
        assertThat(facts("asset", ASSET)).isEmpty();
    }

    @Test
    @DisplayName("a guard needs only its schema version, id, title, interface and implementation")
    void aMinimalGuardValidates() {
        String minimal = """
                schema_version: '1.0'
                id: review-ref
                title: Review reference present
                interface: guard/1
                implemented_by: builtin:review-ref
                """;

        assertThat(facts("guard", minimal)).isEmpty();
    }

    @ParameterizedTest(name = "a guard with {1} is refused with: {2}")
    @MethodSource("guardTwins")
    void eachGuardTwinIsRefusedForItsOneProperty(String line, String replacement, String expected) {
        assertThat(facts("guard", twin(GUARD, line, replacement))).containsExactly(expected);
    }

    @Test
    @DisplayName("a guard written with a pack-qualified asset validates (ADR-0053 decision 7)")
    void aPackQualifiedAssetValidates() {
        String qualified =
                twin(GUARD, "implemented_by: asset:carrier-lint", "implemented_by: qa::asset:qa-traceability-lint");

        assertThat(facts("guard", qualified)).isEmpty();
    }

    @ParameterizedTest(name = "a lifecycle with {1} is refused with: {2}")
    @MethodSource("lifecycleTwins")
    void eachLifecycleTwinIsRefusedForItsOneProperty(String line, String replacement, String expected) {
        List<String> facts = facts("lifecycle", twin(LIFECYCLE, line, replacement));

        if (expected.startsWith("~")) {
            // A from that fails its array branch also fails the other anyOf branches: that is the validator's
            // structure, so only the expected keyword and the pointer are pinned.
            String keyword = expected.substring(1);
            String pointer = pointerOf(keyword);
            assertThat(facts)
                    .contains(keyword)
                    .allSatisfy(fact -> assertThat(pointerOf(fact)).isEqualTo(pointer));
        } else {
            assertThat(facts).containsExactlyInAnyOrder(expected.split("; "));
        }
    }

    @Test
    @DisplayName("a lifecycle transition's guard may name a guard of another pack (ADR-0053 decision 7)")
    void aPackQualifiedGuardRefValidates() {
        String qualified = twin(
                LIFECYCLE,
                "  - {ref: subtasks-ready, blocking: false, config: {strict: true}}",
                "  - {ref: 'qa::guard:traceability-ready'}");

        assertThat(facts("lifecycle", qualified)).isEmpty();
    }

    @ParameterizedTest(name = "an executable with {1} is refused with: {2}")
    @MethodSource("executableTwins")
    void eachExecutableTwinIsRefusedForItsOneProperty(String line, String replacement, String expected) {
        assertThat(facts("asset", twin(ASSET, line, replacement))).containsExactly(expected);
    }

    @Test
    @DisplayName("the schema's builtin: list is exactly the domain's closed list, and its runtimes the allowlist")
    void theClosedListsAreTheDomains() {
        JsonNode guard = bundled("guard");
        Matcher builtins = Pattern.compile("builtin:\\(([a-z|-]+)\\)")
                .matcher(guard.get("properties")
                        .get("implemented_by")
                        .get("pattern")
                        .asString());
        assertThat(builtins.find()).isTrue();

        assertThat(new TreeSet<>(Arrays.asList(builtins.group(1).split("\\|"))))
                .isEqualTo(new TreeSet<>(Arrays.stream(BuiltinGuard.values())
                        .map(BuiltinGuard::id)
                        .toList()));
        assertThat(bundled("asset")
                        .get("$defs")
                        .get("Executable")
                        .get("properties")
                        .get("runtime")
                        .get("enum"))
                .extracting(JsonNode::asString)
                .containsExactlyElementsOf(Arrays.stream(ExecutableRuntime.values())
                        .map(ExecutableRuntime::token)
                        .toList());
    }

    @Test
    @DisplayName("the schema and the domain accept and refuse the same entry and min_version strings")
    void theExecutablePatternsAreTheSchemas() {
        for (String entry : List.of(
                "carrier_lint.py",
                "a/b.py",
                "a-b_c.d/e.py",
                "../x",
                "a/../x",
                ".hidden",
                "a/.hidden",
                "a\\b",
                "-x",
                "/abs",
                "a b",
                "a//b",
                "a/",
                "")) {
            boolean schema = facts("asset", twin(ASSET, "  entry: carrier_lint.py", "  entry: '" + entry + "'"))
                    .isEmpty();
            boolean domain =
                    accepted(() -> new Executable(ExecutableRuntime.PYTHON, Optional.empty(), entry, List.of()));
            assertThat(domain)
                    .as("entry '%s': the domain agrees with the schema (%s)", entry, schema)
                    .isEqualTo(schema);
        }
        for (String version : List.of("3", "3.11", "3.11.2", "3.11.2.1", "3.x", "v3", "3.", "")) {
            boolean schema = facts("asset", twin(ASSET, "  min_version: '3.11'", "  min_version: '" + version + "'"))
                    .isEmpty();
            boolean domain = accepted(
                    () -> new Executable(ExecutableRuntime.PYTHON, Optional.of(version), "carrier_lint.py", List.of()));
            assertThat(domain)
                    .as("min_version '%s': the domain agrees with the schema (%s)", version, schema)
                    .isEqualTo(schema);
        }
    }

    /** The pointer of a fact, {@code <keyword> <pointer> <property>}. */
    private static String pointerOf(String fact) {
        int from = fact.indexOf(' ') + 1;
        return fact.substring(from, fact.indexOf(' ', from));
    }

    private static boolean accepted(Runnable build) {
        try {
            build.run();
            return true;
        } catch (ConstraintBreachException refused) {
            return false;
        }
    }

    @Test
    @DisplayName("config_schema and an entry's config are the only open objects of the two schemas (ADR-0042 S4)")
    void theOnlyOpenObjectsAreTheAmendedOnes() {
        List<SchemaViolation> openConfig = PackSchemas.bundled()
                .validate(
                        "lifecycle",
                        "x.lifecycle.yaml",
                        Yaml.load(twin(
                                LIFECYCLE,
                                "  - {ref: subtasks-ready, blocking: false, config: {strict: true}}",
                                "  - {ref: subtasks-ready, config: {any: {nested: [1, two]}}}")));
        List<SchemaViolation> openSchema = PackSchemas.bundled()
                .validate(
                        "guard",
                        "x.guard.yaml",
                        Yaml.load(twin(
                                GUARD,
                                "config_schema: {type: object}",
                                "config_schema: {type: object, properties: {a: {const: 1}}}")));

        assertThat(openConfig).isEmpty();
        assertThat(openSchema).isEmpty();
        assertThat(Map.of(
                        "guard", openObjects(bundled("guard"), "", new java.util.ArrayList<>()),
                        "lifecycle", openObjects(bundled("lifecycle"), "", new java.util.ArrayList<>())))
                .containsEntry("guard", List.of("/properties/config_schema"))
                .containsEntry("lifecycle", List.of("/$defs/GuardEntry/properties/config"));
    }

    private static JsonNode bundled(String kind) {
        try (var in = GuardLifecycleSchemaTest.class.getResourceAsStream("/packs/schemas/" + kind + ".schema.json")) {
            assertThat(in).isNotNull();
            return JsonMapper.builder().build().readTree(in);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Every {@code type: object} that does not close itself with {@code additionalProperties: false}. */
    private static List<String> openObjects(JsonNode node, String at, List<String> found) {
        if (node.isObject()) {
            JsonNode type = node.get("type");
            JsonNode additional = node.get("additionalProperties");
            if (type != null
                    && type.isString()
                    && type.asString().equals("object")
                    && (additional == null || !additional.isBoolean() || additional.booleanValue())) {
                found.add(at.isEmpty() ? "/" : at);
            }
            node.properties().forEach(child -> openObjects(child.getValue(), at + "/" + child.getKey(), found));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                openObjects(node.get(i), at + "/" + i, found);
            }
        }
        return found;
    }
}
