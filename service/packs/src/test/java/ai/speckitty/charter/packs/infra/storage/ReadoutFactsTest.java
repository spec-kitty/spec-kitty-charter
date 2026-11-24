package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The readout's composition facts ride on the one read (#478, FR-003, S1, S10): {@link PackReadout#facts()} is built
 * from the same walk and store half as the rest of the readout, or carries the refusal that stopped it, and never
 * throws.
 */
@DisplayName("PackReadout.facts: the composition facts of the one read, or the refusal that stopped them (#478, S1)")
class ReadoutFactsTest {

    private static final YamlSerializationStrategy STRATEGY = new YamlSerializationStrategy();

    static List<String> examplePacks() {
        return ExamplePacks.names();
    }

    private static PackReadout read(String name, Path directory) {
        return new FileSystemPackInspector()
                .read(new PackRoot(name, Optional.of(ExamplePacks.level(name)), directory.toString()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examplePacks")
    @DisplayName("each example pack: the readout's facts are the pack under resolution's, and so the recorded ones")
    void theReadoutsFactsAreThePackUnderResolutions(String name, @TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(name, host);
        PackUnderResolution expected = CompositionFactsBuilder.underResolution(
                new ConfiguredPack(new PackIdentifier(name), ExamplePacks.level(name)), pack, STRATEGY);

        PackReadout readout = read(name, pack);

        assertThat(readout.facts()).isInstanceOfSatisfying(PackReadout.Built.class, built -> {
            JsonNode recorded = recorded().required(name);
            assertThat(built.descriptor()).isEqualTo(expected.descriptor());
            assertThat(built.descriptor().packId())
                    .isEqualTo(recorded.required("packId").asString());
            assertThat(built.facts().changed()).isEqualTo(expected.changedArtifacts());
            assertThat(built.facts().changed()).isEqualTo(strings(recorded.required("changed")));
            assertThat(built.facts().referable()).isEqualTo(expected.referableArtifacts());
            assertThat(built.facts().held()).isEqualTo(strings(recorded.required("held")));
            assertThat(built.facts().declared()).isEqualTo(strings(recorded.required("declared")));
            assertThat(built.facts().referable()).isEqualTo(strings(recorded.required("referable")));
            assertThat(Set.copyOf(built.facts().terms())).isEqualTo(expected.heldTerms());
            assertThat(Set.copyOf(built.facts().terms()))
                    .isEqualTo(Set.copyOf(strings(recorded.required("heldTerms"))));
        });
    }

    @Test
    @DisplayName("a graph document that cannot be read: Unreadable(pack_unreadable), worded as the composing read")
    void anUnreadableGraphIsUnreadable(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        Files.writeString(pack.resolve("broken.graph.yaml"), "nodes: [valid\n", StandardCharsets.UTF_8);

        PackReadout readout = read(ExamplePacks.BUILT_IN, pack);

        assertThat(readout.facts())
                .isEqualTo(new PackReadout.Unreadable(
                        RefusalCode.PACK_UNREADABLE,
                        "a graph document of built-in cannot be read: built-in/broken.graph.yaml: while parsing a flow"
                                + " sequence"));
    }

    @Test
    @DisplayName("a file the composing read cannot read: Unreadable(pack_unreadable), naming the file")
    void anUnreadableFileIsUnreadable(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        Files.writeString(pack.resolve("tactics/broken.tactic.yaml"), "id: [\n", StandardCharsets.UTF_8);

        PackReadout readout = read(ExamplePacks.BUILT_IN, pack);

        assertThat(readout.facts()).isInstanceOfSatisfying(PackReadout.Unreadable.class, unreadable -> {
            assertThat(unreadable.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(unreadable.detail())
                    .startsWith("the file tactics/broken.tactic.yaml of built-in cannot be read");
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = RefusalCode.class,
            names = {"VALIDATION_FAILED", "DRG_SCHEMA_ERROR"})
    @DisplayName("a load refused with another code: Unreadable carries that code and the refusal's detail (S10)")
    void aLoadRefusedWithAnotherCodeKeepsItsCode(RefusalCode code) {
        StoreRead refused = new StoreRead(
                new Assembly.Refused(List.of(), code, "the aggregate of built-in breaks an invariant", false),
                new PackLayout(Map.of()),
                List.of(),
                Optional.empty(),
                List.of());

        PackReadout.Facts facts = CompositionFactsBuilder.readoutFacts("built-in", refused);

        assertThat(facts).isEqualTo(new PackReadout.Unreadable(code, "the aggregate of built-in breaks an invariant"));
    }

    @Test
    @DisplayName("an aggregate breach (StoreRead.breached): Unreadable(validation_failed), not pack_unreadable")
    void anAggregateBreachIsValidationFailed() {
        StoreRead breached = StoreRead.breached(List.of(), "two artifacts share the id x");

        PackReadout.Facts facts = CompositionFactsBuilder.readoutFacts("built-in", breached);

        assertThat(facts)
                .isEqualTo(new PackReadout.Unreadable(RefusalCode.VALIDATION_FAILED, "two artifacts share the id x"));
    }

    @Test
    @DisplayName("a read with no assembly input: Unreadable(pack_unreadable) in the one wording, not an exception")
    void aReadWithNoAssemblyIsUnreadable() {
        StoreRead none =
                new StoreRead(new Assembly.None(), new PackLayout(Map.of()), List.of(), Optional.empty(), List.of());

        PackReadout.Facts facts = CompositionFactsBuilder.readoutFacts("built-in", none);

        assertThat(facts)
                .isEqualTo(new PackReadout.Unreadable(
                        RefusalCode.PACK_UNREADABLE, "the pack built-in cannot be read: it has no assembly"))
                .isEqualTo(PackReadout.Unreadable.noAssembly("built-in"));
    }

    @Test
    @DisplayName("a pack with no pack.yaml: Unreadable(pack_unreadable), not an exception")
    void aPackWithoutADescriptorIsUnreadable(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        Files.delete(pack.resolve("pack.yaml"));

        PackReadout readout = read(ExamplePacks.BUILT_IN, pack);

        assertThat(readout.facts())
                .isEqualTo(new PackReadout.Unreadable(RefusalCode.PACK_UNREADABLE, "pack built-in has no pack.yaml"));
    }

    @Test
    @DisplayName("a root that is not a directory: Unreadable(pack_unreadable), not an exception")
    void aRootThatIsNoDirectoryIsUnreadable(@TempDir Path host) {
        PackReadout readout = read(ExamplePacks.BUILT_IN, host.resolve("absent"));

        assertThat(readout.facts())
                .as(
                        "the store half names the directory it was given; the verdict's root fault carries the configured name")
                .isEqualTo(new PackReadout.Unreadable(RefusalCode.PACK_UNREADABLE, "pack absent is not a directory"));
    }

    @Test
    @DisplayName("a directory whose name is not a pack id: Unreadable, not an exception, and one pack_id_invalid fault")
    void aNameThatIsNoPackIdIsUnreadableNotAnException(@TempDir Path host) throws IOException {
        Path pack = Files.move(ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host), host.resolve("Bad_Name"));

        PackReadout readout = read(ExamplePacks.BUILT_IN, pack);
        PackReadout named = new FileSystemPackInspector()
                .read(new PackRoot(
                        "Bad_Name", Optional.of(ExamplePacks.level(ExamplePacks.BUILT_IN)), pack.toString()));

        assertThat(readout.facts())
                .as("under a pack id, the directory's name is not judged")
                .isInstanceOf(PackReadout.Built.class);
        assertThat(named.facts())
                .isEqualTo(
                        new PackReadout.Unreadable(RefusalCode.PACK_UNREADABLE, StoreRead.packIdInvalid("Bad_Name")));
        assertThat(named.rationale().violations())
                .as("#583 D5: the read's one fault; the facts' refusal is the same cause")
                .singleElement()
                .satisfies(reason -> assertThat(reason.code().value()).isEqualTo("pack_id_invalid"));
    }

    @Test
    @DisplayName("one read: a readout with its facts walks the tree once and runs the store half once")
    void theFactsCostNoSecondRead(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        ReadCounters before = ReadCounters.now();

        PackReadout readout = read(ExamplePacks.BUILT_IN, pack);

        assertThat(readout.facts()).isInstanceOf(PackReadout.Built.class);
        assertThat(ReadCounters.now().since(before)).isEqualTo(new ReadCounters(1, 1, 1));
    }

    private static JsonNode recorded() {
        try (InputStream in = Objects.requireNonNull(
                ReadoutFactsTest.class.getResourceAsStream("/facts/example-packs.json"), "/facts/example-packs.json")) {
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }
}
