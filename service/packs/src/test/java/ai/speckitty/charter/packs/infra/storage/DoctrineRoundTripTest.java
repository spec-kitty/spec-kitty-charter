package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.ConfigValue;
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
                List.of("first", "second", "first"),
                List.of("rule"),
                List.of("criterion"),
                List.of("allowance"),
                List.of(
                        new DirectiveDso.Reference("tactic", "t-1", Optional.of("when ready"), Optional.of("why")),
                        new DirectiveDso.Reference("tactic", "t-1", Optional.empty(), Optional.empty()),
                        new DirectiveDso.Reference("styleguide", "s-1", Optional.of("w"), Optional.empty())));

        assertThat(read(yaml.readDirective(yaml.writeDirective(dso), FILE))).isEqualTo(dso);
    }

    @Test
    void aMinimalDirectiveNormalizesAbsentListsToEmpty() {
        DirectiveDso dso = new DirectiveDso(
                "D",
                "1.0",
                "T",
                "I",
                "advisory",
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(read(yaml.readDirective(yaml.writeDirective(dso), FILE))).isEqualTo(dso);
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
                List.of("java"),
                List.of("ref-1", "ref-1"));

        StyleguideDso back = read(yaml.readStyleguide(yaml.writeStyleguide(dso), FILE));

        assertThat(back).isEqualTo(dso);
        assertThat(back.tooling().orElseThrow().keySet()).containsExactly("zeta", "alpha");
    }

    @Test
    void presentEmptyAndAbsentOptionalsStayDistinct() {
        StyleguideDso present = styleguide(Optional.of(Map.of()), Optional.of(ConfigValue.of(new LinkedHashMap<>())));
        StyleguideDso absent = styleguide(Optional.empty(), Optional.empty());

        assertThat(read(yaml.readStyleguide(yaml.writeStyleguide(present), FILE)))
                .isEqualTo(present);
        assertThat(read(yaml.readStyleguide(yaml.writeStyleguide(absent), FILE)))
                .isEqualTo(absent);
        assertThat(present).isNotEqualTo(absent);
    }

    @Test
    void aParadigmSurvivesTheRoundTrip() {
        ParadigmDso dso = new ParadigmDso(
                "para-1",
                "1.0",
                "Name",
                "Summary",
                List.of("DIRECTIVE_001", "DIRECTIVE_002"),
                List.of(new ParadigmDso.Reference("tactic", "t"), new ParadigmDso.Reference("tactic", "t")));

        assertThat(read(yaml.readParadigm(yaml.writeParadigm(dso), FILE))).isEqualTo(dso);
    }

    /** FR-016: the schemas are closed-world; the control proves the same fixture reads clean without the stray key. */
    @Test
    void anUnknownTopLevelKeyIsUnreadableWhileTheKnownKeyControlReadsClean() {
        String clean = "id: D\nschema_version: '1.0'\ntitle: T\nintent: I\nenforcement: advisory\n";

        assertThat(yaml.readDirective(clean, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.readDirective(clean + "surprise: 1\n", FILE)).isInstanceOf(FileRead.Unreadable.class);
        String sg = "id: s\nschema_version: '1.0'\ntitle: T\nscope: code\nprinciples: [p]\n";
        assertThat(yaml.readStyleguide(sg, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.readStyleguide(sg + "surprise: 1\n", FILE)).isInstanceOf(FileRead.Unreadable.class);
        String pa = "id: p\nschema_version: '1.0'\nname: N\nsummary: S\n";
        assertThat(yaml.readParadigm(pa, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.readParadigm(pa + "surprise: 1\n", FILE)).isInstanceOf(FileRead.Unreadable.class);
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
                List.of(),
                List.of());
    }
}
