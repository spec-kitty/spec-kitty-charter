package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A guard, a lifecycle and an executable asset sidecar round-trip through their YAML formats and bindings: every field
 * survives DSO, YAML, DSO, entity and DSO again, the written text means the source (ADR-0036), keys are written in
 * schema order, and an optional stays absent or stated as authored.
 */
@DisplayName("Guards, lifecycles and executable assets round-trip through the YAML format and the domain (#156)")
class GuardAndLifecycleYamlTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M65RTQZ8GVARD11FECYC01EA");
    private static final String DIR = "/roundtrip-fixtures/guard-lifecycle/";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @Test
    @DisplayName("a guard with every field round-trips, keys in schema order, its config_schema kept as a tree")
    void theFullGuardRoundTrips() throws IOException {
        String source = fixture("guards/traceability-ready.guard.yaml");

        String written = roundTrip(GuardBinding.INSTANCE, source, "guard");

        assertThat(map(Yaml.load(written)).keySet())
                .containsExactly(
                        "schema_version",
                        "id",
                        "title",
                        "description",
                        "interface",
                        "implemented_by",
                        "config_schema",
                        "limits",
                        "on_indeterminate");
        assertThat(map(map(Yaml.load(written)).get("limits")))
                .as("a stated default is kept as stated")
                .containsEntry("network", false)
                .containsEntry("timeout_seconds", 120);
    }

    @Test
    @DisplayName("a built-in guard writes no optional key back")
    void theMinimalGuardWritesNoOptionalKey() throws IOException {
        String source = fixture("guards/subtasks-ready.guard.yaml");

        String written = roundTrip(GuardBinding.INSTANCE, source, "guard");

        assertThat(map(Yaml.load(written)).keySet())
                .containsExactly("schema_version", "id", "title", "interface", "implemented_by");
    }

    @Test
    @DisplayName("a pack-qualified implemented_by and guard ref write back as authored (ADR-0053 decision 7)")
    void qualifiedReferencesRoundTrip() throws IOException {
        String guard = fixture("guards/traceability-ready.guard.yaml")
                .replace("asset:carrier-lint", "qa::asset:qa-traceability-lint");
        String lifecycle = fixture("lifecycles/work-package.lifecycle.yaml")
                .replace("  - ref: traceability-ready\n", "  - ref: qa::guard:traceability-ready\n");

        assertThat(map(Yaml.load(roundTrip(GuardBinding.INSTANCE, guard, "guard"))))
                .containsEntry("implemented_by", "qa::asset:qa-traceability-lint");
        assertThat(roundTrip(LifecycleBinding.INSTANCE, lifecycle, "lifecycle"))
                .contains("ref: qa::guard:traceability-ready");
    }

    @Test
    @DisplayName("a lifecycle round-trips, each from in the form it was authored in: one state, a list or *")
    void theLifecycleRoundTrips() throws IOException {
        String source = fixture("lifecycles/work-package.lifecycle.yaml");

        String written = roundTrip(LifecycleBinding.INSTANCE, source, "lifecycle");

        Map<String, Object> root = map(Yaml.load(written));
        assertThat(root.keySet())
                .containsExactly("schema_version", "id", "title", "description", "initial", "states", "transitions");
        List<?> transitions = (List<?>) root.get("transitions");
        assertThat(transitions)
                .extracting(t -> map(t).get("from"))
                .containsExactly(
                        "planned",
                        "in_progress",
                        List.of("for_review", "blocked"),
                        "for_review",
                        "*",
                        List.of("planned", "in_progress"));
        assertThat(map(transitions.get(0)).keySet()).containsExactly("from", "to", "event");
        assertThat(map(transitions.get(1)).keySet()).containsExactly("from", "to", "guard");
        assertThat((List<?>) map(transitions.get(1)).get("guard"))
                .extracting(g -> List.copyOf(map(g).keySet()))
                .containsExactly(List.of("ref"), List.of("ref", "config", "blocking"));
    }

    @Test
    @DisplayName("an asset sidecar's executable block round-trips, written last in schema order")
    void theExecutableAssetRoundTrips() throws IOException {
        String source = fixture("assets/carrier_lint.py.asset.yaml");

        String written = roundTrip(AssetBinding.INSTANCE, source, "asset");

        assertThat(map(Yaml.load(written)).keySet()).containsExactly("id", "mime", "path", "title", "executable");
        assertThat(map(map(Yaml.load(written)).get("executable")).keySet())
                .containsExactly("runtime", "min_version", "entry", "args");
    }

    @Test
    @DisplayName("an asset sidecar without an executable block writes none back")
    void anAssetWithoutAnExecutableWritesNone() throws IOException {
        String source = fixture("assets/notes.txt.asset.yaml");

        String written = roundTrip(AssetBinding.INSTANCE, source, "asset");

        assertThat(map(Yaml.load(written))).doesNotContainKey("executable");
    }

    private <D> String roundTrip(KindBinding<D, ?> binding, String source, String kind) {
        D dso = read(binding, source);
        String written = strategy.write(binding, dso);
        D reread = read(binding, written);
        D throughTheDomain = binding.dsoOf(binding.toEntity(PACK, reread));

        assertThat(reread).isEqualTo(dso);
        assertThat(throughTheDomain).isEqualTo(dso);
        assertThat(MeaningComparator.adr0036().compareTexts(kind, source, written))
                .isEmpty();
        assertThat(strategy.write(binding, throughTheDomain))
                .as("the write is stable")
                .isEqualTo(written);
        return written;
    }

    private <D> D read(KindBinding<D, ?> binding, String text) {
        FileRead<D> read = strategy.read(binding, text, "probe." + binding.fileToken() + ".yaml");
        assertThat(read).as("it reads").isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<D>) read).value();
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = GuardAndLifecycleYamlTest.class.getResourceAsStream(DIR + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked") // a YAML mapping read back from text this test wrote
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
