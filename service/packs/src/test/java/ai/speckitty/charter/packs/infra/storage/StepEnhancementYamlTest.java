package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.StepEnhancement;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A step enhancement round-trips through its YAML format, its companions and its binding by meaning (ADR-0036,
 * ADR-0066): every field survives DSO, YAML, DSO, entity and DSO again, and each fragment's body is compared, so a
 * fragment that is dropped or altered fails the assertion. The mutant cases show the assertion is not vacuous.
 */
@DisplayName("A step enhancement and its fragments round-trip by meaning (#428)")
class StepEnhancementYamlTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M6ZRYK0000000000000000E1");
    private static final StepEnhancementBinding BINDING = StepEnhancementBinding.INSTANCE;

    private static final String SOURCE = """
            schema_version: '1.0'
            id: spdd-plan
            prompt_fragments:
              - slot: spdd:reasons-block
                path: spdd-plan.prompt-fragment.md
              - slot: spdd:canvas
                path: spdd-canvas.prompt-fragment.md
            """;

    private static final Map<String, String> FRAGMENTS = Map.of(
            "spdd-plan.prompt-fragment.md",
            "Fill or update the REASONS canvas while composing the plan.\n",
            "spdd-canvas.prompt-fragment.md",
            "# Canvas\n\nKeep the canvas beside the plan.\n\n## Approach\n\nName the chosen strategy.\n");

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @Test
    @DisplayName("an enhancement with two entries and two companions round-trips, fragment bodies included")
    void twoEntriesAndTwoCompanionsRoundTrip() {
        StepEnhancementDso source = withBodies(read(SOURCE), FRAGMENTS);

        Written written = write(source);
        StepEnhancementDso reread = withBodies(read(written.yaml()), written.fragments());

        assertSameMeaning(source, reread);
        assertThat(reread).isEqualTo(source);
        assertSameMeaning(source, BINDING.dsoOf(BINDING.toEntity(PACK, reread)));
        assertThat(MeaningComparator.adr0036().compareTexts("step-enhancement", SOURCE, written.yaml()))
                .isEmpty();
        assertThat(write(reread)).as("the write is stable").isEqualTo(written);
    }

    @Test
    @DisplayName(
            "the companions are one per entry, in entry order, at the entry's path, Markdown with the fragment schema")
    void theCompanionsFollowTheEntries() {
        StepEnhancementDso source = withBodies(read(SOURCE), FRAGMENTS);

        assertThat(BINDING.hasCompanions()).isTrue();
        assertThat(BINDING.companions(source))
                .extracting(KindBinding.Companion::path)
                .containsExactly("spdd-plan.prompt-fragment.md", "spdd-canvas.prompt-fragment.md");
        assertThat(BINDING.companions(source)).allSatisfy(companion -> {
            assertThat(companion.form()).isEqualTo(ContentForm.MARKDOWN);
            assertThat(companion.structureSchema()).contains("prompt-fragment");
            assertThat(companion.content()).isPresent();
        });
        assertThat(BINDING.companionStructures()).containsExactly("prompt-fragment");
    }

    @Test
    @DisplayName("mutant: a written fragment that is altered fails the round-trip assertion")
    void anAlteredFragmentFailsTheAssertion() {
        StepEnhancementDso source = withBodies(read(SOURCE), FRAGMENTS);
        Map<String, String> altered = new LinkedHashMap<>(write(source).fragments());
        altered.put("spdd-plan.prompt-fragment.md", "Fill nothing while composing the plan.\n");

        StepEnhancementDso reread = withBodies(read(write(source).yaml()), altered);

        assertThatThrownBy(() -> assertSameMeaning(source, reread)).isInstanceOf(AssertionError.class);
        assertThat(reread).isNotEqualTo(source);
    }

    @Test
    @DisplayName("mutant: a written fragment that is dropped fails the round-trip assertion")
    void aDroppedFragmentFailsTheAssertion() {
        StepEnhancementDso source = withBodies(read(SOURCE), FRAGMENTS);
        Map<String, String> dropped = new LinkedHashMap<>(write(source).fragments());
        dropped.remove("spdd-canvas.prompt-fragment.md");

        StepEnhancementDso reread = withBodies(read(write(source).yaml()), dropped);

        assertThatThrownBy(() -> assertSameMeaning(source, reread)).isInstanceOf(AssertionError.class);
        assertThat(reread).isNotEqualTo(source);
    }

    @Test
    @DisplayName("an enhancement with no prompt_fragments key reads as no entry, builds an entity and writes no key")
    void anAbsentKeyIsAnEmptyListNotAnError() {
        String bare = "schema_version: '1.0'\nid: bare\n";

        StepEnhancementDso dso = read(bare);

        assertThat(dso.promptFragments()).isEmpty();
        StepEnhancement entity = BINDING.toEntity(PACK, dso);
        assertThat(entity.promptFragments()).isEmpty();
        assertThat(BINDING.dsoOf(entity)).isEqualTo(dso);
        assertThat(strategy.write(BINDING, dso)).doesNotContain("prompt_fragments");
    }

    @Test
    @DisplayName("the entity carries no target: its only accessors are the id, the version and the entries")
    void theEntityHasNoTargetField() {
        assertThat(StepEnhancement.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("target", "range", "anchor", "position", "priority");
    }

    private record Written(String yaml, Map<String, String> fragments) {}

    private Written write(StepEnhancementDso dso) {
        Map<String, String> fragments = new LinkedHashMap<>();
        for (KindBinding.Companion companion : BINDING.companions(dso)) {
            companion.content().ifPresent(content -> {
                assertThat(content).isInstanceOf(CompanionContent.Markdown.class);
                fragments.put(companion.path(), MarkdownCodec.write(((CompanionContent.Markdown) content).document()));
            });
        }
        return new Written(strategy.write(BINDING, dso), fragments);
    }

    private StepEnhancementDso read(String text) {
        FileRead<StepEnhancementDso> read = strategy.read(BINDING, text, "probe.step-enhancement.yaml");
        assertThat(read).as("it reads").isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<StepEnhancementDso>) read).value();
    }

    /** The shape with each companion's body filled in from {@code texts}, as the store fills them in after a read. */
    private static StepEnhancementDso withBodies(StepEnhancementDso dso, Map<String, String> texts) {
        Map<String, CompanionContent> contents = new LinkedHashMap<>();
        texts.forEach((path, text) -> {
            MarkdownCodec.Outcome outcome = MarkdownCodec.parse(text);
            assertThat(outcome).isInstanceOf(MarkdownCodec.Parsed.class);
            contents.put(path, new CompanionContent.Markdown(((MarkdownCodec.Parsed) outcome).document()));
        });
        return BINDING.withCompanions(dso, contents);
    }

    /** Fails on the first difference in an entry's slot or path, or in a fragment body's meaning. */
    private static void assertSameMeaning(StepEnhancementDso expected, StepEnhancementDso actual) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.schemaVersion()).isEqualTo(expected.schemaVersion());
        assertThat(actual.promptFragments()).hasSameSizeAs(expected.promptFragments());
        List<String> differences = new ArrayList<>();
        for (int i = 0; i < expected.promptFragments().size(); i++) {
            StepEnhancementDso.Entry want = expected.promptFragments().get(i);
            StepEnhancementDso.Entry got = actual.promptFragments().get(i);
            assertThat(got.slot()).isEqualTo(want.slot());
            assertThat(got.path()).isEqualTo(want.path());
            assertThat(got.body()).as("the body of " + want.path()).isPresent();
            MarkdownDocument.differences(want.body().orElseThrow(), got.body().orElseThrow())
                    .forEach(path -> differences.add(want.path() + ": " + path));
        }
        assertThat(differences).as("the fragment bodies differ").isEmpty();
    }
}
