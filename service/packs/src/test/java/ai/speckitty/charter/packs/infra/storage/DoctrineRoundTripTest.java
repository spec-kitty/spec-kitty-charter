package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The fidelity bar of the doctrine kinds is DSO-record equality: {@code read(write(dso))} equals {@code dso}. The
 * styleguide fixture carries the hard cases: mixed scalar types, a nested seq/map, a present-empty map and an
 * explicit null, none of which may be coerced or dropped.
 */
class DoctrineRoundTripTest {

    private static final String FILE = "x.yaml";
    private final SerializationStrategy yaml = new YamlSerializationStrategy();

    private static <T> T read(FileRead<T> result) {
        return switch (result) {
            case FileRead.Read<T> ok -> ok.value();
            default -> throw new AssertionError("expected a clean read but got " + result);
        };
    }

    @Test
    void aDirectiveSurvivesTheRoundTrip() {
        DirectiveDso dso = new DirectiveDso(
                "DIRECTIVE_001",
                "1.0",
                "Title",
                "Intent",
                "lenient-adherence",
                Optional.of("global"),
                List.of("rule", "other", "rule"),
                List.of("criterion"),
                List.of("allowance"));

        assertThat(read(yaml.read(DirectiveBinding.INSTANCE, yaml.write(DirectiveBinding.INSTANCE, dso), FILE)))
                .isEqualTo(dso);
    }

    @Test
    void aMinimalDirectiveNormalizesAbsentListsToEmpty() {
        DirectiveDso dso =
                new DirectiveDso("D", "1.0", "T", "I", "advisory", Optional.empty(), List.of(), List.of(), List.of());

        assertThat(read(yaml.read(DirectiveBinding.INSTANCE, yaml.write(DirectiveBinding.INSTANCE, dso), FILE)))
                .isEqualTo(dso);
    }

    @Test
    void aStyleguideKeepsToolingOrderAndEveryConfigValueType() {
        Map<String, ConfigValue> lint = new LinkedHashMap<>();
        lint.put("limit", ConfigValue.of(42));
        lint.put("ratio", ConfigValue.of(1.5));
        lint.put("strict", ConfigValue.of(true));
        lint.put("quotedNumber", ConfigValue.of("42"));
        lint.put("nothing", ConfigValue.of(null));
        lint.put("emptyMap", ConfigValue.of(new LinkedHashMap<String, Object>()));
        lint.put("list", ConfigValue.of(List.of("a", 2, Map.of("k", "v"), List.of("nested"))));
        Map<String, String> tooling = new LinkedHashMap<>();
        tooling.put("zeta", "z");
        tooling.put("alpha", "a");
        StyleguideDso dso = new StyleguideDso(
                "sg-1",
                "1.0",
                "Title",
                "code",
                List.of("p1", "p2"),
                List.of(
                        new StyleguideDso.Pattern("n", "d", Optional.of("bad"), Optional.of("good")),
                        new StyleguideDso.Pattern("m", "e", Optional.empty(), Optional.empty())),
                List.of(new StyleguideDso.AntiPattern("a", "b", "c", "d")),
                Optional.of(tooling),
                Optional.of("quality"),
                Optional.of(new ConfigValue.Mapping(new LinkedHashMap<>(lint))),
                List.of("java"));

        StyleguideDso back =
                read(yaml.read(StyleguideBinding.INSTANCE, yaml.write(StyleguideBinding.INSTANCE, dso), FILE));

        assertThat(back).isEqualTo(dso);
        assertThat(back.tooling().orElseThrow().keySet()).containsExactly("zeta", "alpha");
        // DSO record equality uses Map.equals for the nested mapping, which is order-insensitive, so the
        // isEqualTo above would not catch a reader/writer that reordered structural_lint_config keys. Pin the
        // authored key sequence explicitly, as tooling already does.
        assertThat(((ConfigValue.Mapping) back.structuralLintConfig().orElseThrow())
                        .entries()
                        .keySet())
                .containsExactly("limit", "ratio", "strict", "quotedNumber", "nothing", "emptyMap", "list");
    }

    @Test
    void presentEmptyAndAbsentOptionalsStayDistinct() {
        StyleguideDso present = styleguide(Optional.of(Map.of()), Optional.of(ConfigValue.of(new LinkedHashMap<>())));
        StyleguideDso absent = styleguide(Optional.empty(), Optional.empty());

        assertThat(read(yaml.read(StyleguideBinding.INSTANCE, yaml.write(StyleguideBinding.INSTANCE, present), FILE)))
                .isEqualTo(present);
        assertThat(read(yaml.read(StyleguideBinding.INSTANCE, yaml.write(StyleguideBinding.INSTANCE, absent), FILE)))
                .isEqualTo(absent);
        assertThat(present).isNotEqualTo(absent);
    }

    @Test
    void aParadigmSurvivesTheRoundTrip() {
        ParadigmDso dso = new ParadigmDso("para-1", "1.0", "Name", "Summary");

        assertThat(read(yaml.read(ParadigmBinding.INSTANCE, yaml.write(ParadigmBinding.INSTANCE, dso), FILE)))
                .isEqualTo(dso);
    }

    @Test
    void aParadigmWithPrinciplesSurvivesTheRoundTripInOrder() {
        ParadigmDso dso = new ParadigmDso(
                "para-1",
                "1.0",
                "Name",
                "Summary",
                List.of(new ParadigmDso.Principle("Isolated", "One test."), new ParadigmDso.Principle("Fast", "Quick.")));

        assertThat(read(yaml.read(ParadigmBinding.INSTANCE, yaml.write(ParadigmBinding.INSTANCE, dso), FILE)))
                .isEqualTo(dso);
    }

    /** ADR-0067: the principles item is closed, the list is one to twelve items, and the caps are schema caps. */
    @Test
    void paradigmPrinciplesFollowTheSchemaWhileTheWellFormedControlReadsClean() {
        String pa = "id: p\nschema_version: '1.0'\nname: N\nsummary: S\n";
        String good = pa + "principles:\n- name: Fast\n  description: Tests run quickly.\n";

        assertThat(yaml.read(ParadigmBinding.INSTANCE, good, FILE)).isInstanceOf(FileRead.Read.class);
        for (String bad : List.of(
                pa + "principles: []\n",
                good + "  surprise: 1\n",
                pa + "principles:\n- name: Fast\n",
                pa + "principles:\n- name: ''\n  description: d\n",
                pa + "principles:\n- name: " + "n".repeat(65) + "\n  description: d\n",
                pa + "principles:\n- name: n\n  description: " + "d".repeat(256) + "\n")) {
            assertThat(yaml.read(ParadigmBinding.INSTANCE, bad, FILE))
                    .as(bad)
                    .isInstanceOf(FileRead.Unreadable.class);
        }
        StringBuilder thirteen = new StringBuilder(pa + "principles:\n");
        for (int i = 1; i <= 13; i++) {
            thirteen.append("- name: P").append(i).append("\n  description: d\n");
        }
        assertThat(yaml.read(ParadigmBinding.INSTANCE, thirteen.toString(), FILE))
                .isInstanceOf(FileRead.Unreadable.class);
    }

    /** ADR-0069: a profile declares role (+ alt-roles) or the deprecated roles list, exactly one shape. */
    @Test
    void profileRolesFollowTheSchemaWhileEachWellFormedShapeReadsClean() {
        String head = "profile-id: p\nname: P\npurpose: Do.\nspecialization:\n  primary-focus: F\n";
        for (String good : List.of(
                head + "role: implementer\n",
                head + "role: implementer\nalt-roles: [reviewer]\n",
                head + "roles: [implementer, reviewer]\n")) {
            assertThat(yaml.read(AgentProfileBinding.INSTANCE, good, FILE)).as(good).isInstanceOf(FileRead.Read.class);
        }
        for (String bad : List.of(
                head,
                head + "role: implementer\nroles: [implementer]\n",
                head + "roles: [implementer]\nalt-roles: [reviewer]\n",
                head + "alt-roles: [reviewer]\n",
                head + "role: implementer\nalt-roles: []\n",
                head + "role: implementer\nalt-roles: [reviewer, reviewer]\n",
                head + "role: " + "r".repeat(65) + "\n",
                head + "role: Implementer\n")) {
            assertThat(yaml.read(AgentProfileBinding.INSTANCE, bad, FILE)).as(bad).isInstanceOf(FileRead.Unreadable.class);
        }
    }

    @Test
    void aProfileKeepsTheShapeItWasAuthoredIn() {
        String head = "profile-id: p\nname: P\npurpose: Do.\nspecialization:\n  primary-focus: F\n";
        AgentProfileDso modern = read(yaml.read(
                AgentProfileBinding.INSTANCE, head + "role: implementer\nalt-roles: [reviewer]\n", FILE));
        AgentProfileDso legacy =
                read(yaml.read(AgentProfileBinding.INSTANCE, head + "roles: [implementer, reviewer]\n", FILE));

        assertThat(modern.role()).contains("implementer");
        assertThat(modern.altRoles()).containsExactly("reviewer");
        assertThat(modern.roles()).isEmpty();
        assertThat(legacy.roles()).containsExactly("implementer", "reviewer");
        assertThat(legacy.role()).isEmpty();
        assertThat(read(yaml.read(AgentProfileBinding.INSTANCE, yaml.write(AgentProfileBinding.INSTANCE, modern), FILE)))
                .isEqualTo(modern);
        assertThat(read(yaml.read(AgentProfileBinding.INSTANCE, yaml.write(AgentProfileBinding.INSTANCE, legacy), FILE)))
                .isEqualTo(legacy);
    }

    /** FR-016: the schemas are closed-world; the control proves the same fixture reads clean without the stray key. */
    @Test
    void anUnknownTopLevelKeyIsUnreadableWhileTheKnownKeyControlReadsClean() {
        String clean = "id: D\nschema_version: '1.0'\ntitle: T\nintent: I\nenforcement: advisory\n";

        assertThat(yaml.read(DirectiveBinding.INSTANCE, clean, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.read(DirectiveBinding.INSTANCE, clean + "surprise: 1\n", FILE))
                .isInstanceOf(FileRead.Unreadable.class);
        String sg = "id: s\nschema_version: '1.0'\ntitle: T\nscope: code\nprinciples: [p]\n";
        assertThat(yaml.read(StyleguideBinding.INSTANCE, sg, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.read(StyleguideBinding.INSTANCE, sg + "surprise: 1\n", FILE))
                .isInstanceOf(FileRead.Unreadable.class);
        String pa = "id: p\nschema_version: '1.0'\nname: N\nsummary: S\n";
        assertThat(yaml.read(ParadigmBinding.INSTANCE, pa, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.read(ParadigmBinding.INSTANCE, pa + "surprise: 1\n", FILE))
                .isInstanceOf(FileRead.Unreadable.class);
    }

    private static StyleguideDso styleguide(Optional<Map<String, String>> tooling, Optional<ConfigValue> lint) {
        return new StyleguideDso(
                "sg",
                "1.0",
                "T",
                "code",
                List.of("p"),
                List.of(),
                List.of(),
                tooling,
                Optional.empty(),
                lint,
                List.of());
    }
}
