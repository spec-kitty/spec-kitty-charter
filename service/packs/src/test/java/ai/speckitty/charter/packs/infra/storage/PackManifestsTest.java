package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The derived fields of a pack manifest (ADR-0043). Every expected hash below was computed outside this code, with
 * Python: {@code hashlib.sha256} over the LF-normalised bytes, and over
 * {@code json.dumps(obj, sort_keys=True, separators=(',', ':'), ensure_ascii=False)}, which is RFC 8785 for data made
 * of strings, lists and objects only.
 */
@DisplayName("Pack manifest derived fields (#195, ADR-0043)")
class PackManifestsTest {

    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);

    private static PackManifestDso.Constituent constituent(String kind, String id, String path, String hash) {
        return new PackManifestDso.Constituent(kind, id, path, hash);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "LF, 'a\\nb\\n', 911169ddaaf146aff539f58c26c489af3b892dff0fe283c1c264c65ae5aa59a2",
        "CRLF, 'a\\r\\nb\\r\\n', 911169ddaaf146aff539f58c26c489af3b892dff0fe283c1c264c65ae5aa59a2",
        "lone CR, 'a\\rb\\r', 911169ddaaf146aff539f58c26c489af3b892dff0fe283c1c264c65ae5aa59a2",
        "mixed, 'a\\r\\nb\\rc\\n', 880553fca8fcea94e325ee2cfb48e5a985cc797f39a14cc6d3cedecfeb2ae4d2",
        "CR then CRLF, 'a\\r\\r\\nb', 38022fd2b8dbc5cb3d2cee74e083edbf59e3d4e13d067ebcb5db633d4cff4d8c",
        "empty, '', e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    })
    @DisplayName("content_hash is SHA-256 of the bytes with CRLF and lone CR as LF")
    void contentHashNormalisesLineEndings(String name, String escaped, String expected) {
        String text = escaped.replace("\\r", "\r").replace("\\n", "\n");

        assertThat(PackManifests.contentHash(text.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("a CR in the bytes changes nothing but the line ending: a lone CR is never dropped")
    void loneCrIsAnLfNotDropped() {
        assertThat(PackManifests.lf("a\rb".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("a\nb".getBytes(StandardCharsets.UTF_8));
        assertThat(PackManifests.lf(new byte[] {0, (byte) 0xff, '\r'})).containsExactly(0, (byte) 0xff, '\n');
    }

    @Test
    @DisplayName("manifest_hash of a manifest with no constituents and no presets")
    void manifestHashOfTheSmallestManifest() {
        PackManifestDso manifest = new PackManifestDso("1.0", A, List.of(), List.of());

        assertThat(PackManifests.canonicalJson(manifest)).isEqualTo("{\"constituents\":[],\"schema_version\":\"1.0\"}");
        assertThat(PackManifests.manifestHash(manifest))
                .isEqualTo("287713e483bf40b6358ba26b2080d4c9e5145f47273ddb4c0093178481d86331");
    }

    @Test
    @DisplayName("manifest_hash sorts keys and covers constituents and presets, never its own value")
    void manifestHashOfAConstituentAndAPreset() {
        PackManifestDso manifest = new PackManifestDso(
                "1.0",
                A,
                List.of(constituent("directive", "DIRECTIVE_001", "directives/001-a.directive.yaml", A)),
                List.of(new PackManifestDso.PresetEntry("minimal", "presets/minimal.yaml", B)));
        String expected = "ee3b813a279ec6e614cac205e0a065525faf7731ff3c649e6a27766ce0d072bb";

        assertThat(PackManifests.manifestHash(manifest)).isEqualTo(expected);
        assertThat(PackManifests.manifestHash(
                        new PackManifestDso("1.0", B, manifest.constituents(), manifest.presets())))
                .as("the manifest's own hash is not hashed")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("strings are escaped as RFC 8785 has it: quote, backslash, controls; non-ASCII as itself")
    void jcsEscapesAndNonAscii() {
        PackManifestDso manifest = new PackManifestDso(
                "1.0", A, List.of(constituent("tactic", "q\"b\\s\tc\u001fé😀", "tactics/x.tactic.yaml", A)), List.of());

        assertThat(PackManifests.jcsString("q\"b\\s\tc\u001fé😀")).isEqualTo("\"q\\\"b\\\\s\\tc\\u001fé😀\"");
        assertThat(PackManifests.jcsString("\b\f\n\r\u0000")).isEqualTo("\"\\b\\f\\n\\r\\u0000\"");
        assertThat(PackManifests.manifestHash(manifest))
                .isEqualTo("d376ba8fd29dbc1b962a39d72e372ecc0987a8ff92dea7eec6938e03ae667ccc");
    }

    @Test
    @DisplayName("recompute stamps every derived field from the bytes and keeps membership")
    void recomputeStampsEveryDerivedField() {
        PackManifestDso stale = new PackManifestDso(
                "1.0",
                A,
                List.of(constituent("directive", "D1", "directives/d1.directive.yaml", A)),
                List.of(new PackManifestDso.PresetEntry("minimal", "presets/minimal.yaml", A)));
        Map<String, byte[]> files = Map.of(
                "directives/d1.directive.yaml", "id: D1\n".getBytes(StandardCharsets.UTF_8),
                "presets/minimal.yaml", "id: minimal\n".getBytes(StandardCharsets.UTF_8));

        PackManifests.Recomputed recomputed =
                PackManifests.recompute(stale, path -> Optional.ofNullable(files.get(path)));
        PackManifestDso stamped = recomputed.stamped();

        assertThat(recomputed.missing()).isEmpty();
        assertThat(stamped.constituents().getFirst().contentHash())
                .isEqualTo(PackManifests.contentHash(files.get("directives/d1.directive.yaml")))
                .isNotEqualTo(A);
        assertThat(stamped.presets().getFirst().contentHash())
                .isEqualTo(PackManifests.contentHash(files.get("presets/minimal.yaml")));
        assertThat(stamped.manifestHash()).isEqualTo(PackManifests.manifestHash(stamped));
        assertThat(stamped.constituents())
                .extracting(PackManifestDso.Constituent::path)
                .containsExactly("directives/d1.directive.yaml");
        assertThat(PackManifests.stale(stamped, path -> Optional.ofNullable(files.get(path))))
                .isEmpty();
    }

    @Test
    @DisplayName("stale names each derived field that does not verify, and each listed path with no file")
    void staleNamesWhatDoesNotVerify() {
        byte[] d1 = "id: D1\n".getBytes(StandardCharsets.UTF_8);
        PackManifestDso fresh = PackManifests.recompute(
                        new PackManifestDso(
                                "1.0", A, List.of(constituent("directive", "D1", "d1.directive.yaml", A)), List.of()),
                        path -> Optional.of(d1))
                .stamped();
        PackManifestDso staleHash = new PackManifestDso(
                "1.0",
                fresh.manifestHash(),
                List.of(constituent("directive", "D1", "d1.directive.yaml", B)),
                List.of());

        assertThat(PackManifests.stale(fresh, path -> Optional.of(d1))).isEmpty();
        assertThat(PackManifests.stale(staleHash, path -> Optional.of(d1)))
                .containsExactly("constituents{d1.directive.yaml}.content_hash", "manifest_hash");
        assertThat(PackManifests.stale(fresh, path -> Optional.empty())).containsExactly("missing: d1.directive.yaml");
    }

    @Test
    @DisplayName("recompute reports a listed path with no file and keeps that entry's hash; a present file is stamped")
    void recomputeReportsAMissingPath() {
        byte[] d1 = "id: D1\n".getBytes(StandardCharsets.UTF_8);
        PackManifestDso listed = new PackManifestDso(
                "1.0",
                A,
                List.of(
                        constituent("directive", "D1", "d1.directive.yaml", A),
                        constituent("directive", "D2", "d2.directive.yaml", B)),
                List.of());

        PackManifests.Recomputed recomputed = PackManifests.recompute(
                listed, path -> "d1.directive.yaml".equals(path) ? Optional.of(d1) : Optional.empty());

        assertThat(recomputed.missing()).containsExactly("d2.directive.yaml");
        assertThat(recomputed.stamped().constituents())
                .extracting(PackManifestDso.Constituent::contentHash)
                .containsExactly(PackManifests.contentHash(d1), B);
        assertThat(PackManifests.recompute(listed, path -> Optional.of(d1)).missing())
                .isEmpty();
    }

    @Test
    @DisplayName("the YAML form writes the schema's key order, leaves out an absent preset list and reads back equal")
    void yamlFormRoundTrips() {
        PackManifestDso manifest = new PackManifestDso(
                "1.0", A, List.of(constituent("directive", "D1", "directives/d1.directive.yaml", B)), List.of());

        String text = PackManifestYaml.write(manifest);

        assertThat(text).startsWith("schema_version: '1.0'\nmanifest_hash: " + A + "\nconstituents:\n");
        assertThat(text).doesNotContain("presets");
        assertThat(PackManifestYaml.read((Map<?, ?>) Yaml.load(text))).isEqualTo(manifest);
        assertThat(PackManifestYaml.write(new PackManifestDso("1.0", A, List.of(), List.of())))
                .contains("constituents: []");
    }

    @Test
    @DisplayName("the YAML form writes presets after constituents and reads them back equal")
    void yamlFormRoundTripsPresets() {
        PackManifestDso manifest = new PackManifestDso(
                "1.0",
                A,
                List.of(constituent("directive", "D1", "directives/d1.directive.yaml", B)),
                List.of(new PackManifestDso.PresetEntry("minimal", "presets/minimal.yaml", A)));

        String text = PackManifestYaml.write(manifest);

        assertThat(text.indexOf("\npresets:\n")).isGreaterThan(text.indexOf("\nconstituents:\n"));
        assertThat(PackManifestYaml.read((Map<?, ?>) Yaml.load(text))).isEqualTo(manifest);
    }
}
