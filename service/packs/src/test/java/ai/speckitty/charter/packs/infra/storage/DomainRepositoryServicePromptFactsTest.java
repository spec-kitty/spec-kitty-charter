package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The prompt facts are filled where the composition facts are built (research R9, T013), from the same verified read,
 * and agree with what the full read of the pack gives (squad A1: the canonical render the facts judge is the load
 * rule's). The examples are charter's own packs (ADR-0070), read-only; the staged test edits a throwaway copy.
 */
@DisplayName("The composition facts carry the prompt facts of the example packs")
class DomainRepositoryServicePromptFactsTest {

    private static final SlotId REASONS = new SlotId("spdd:reasons-block");
    private static final Set<String> SLOTTED = Set.of("specify", "plan", "tasks", "implement", "review");

    @TempDir
    private Path scratch;

    private static PromptFacts promptsOf(String name) {
        return promptsOf(new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)));
    }

    private static PromptFacts promptsOf(PackLocation location) {
        DomainRepositoryService store =
                new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
        return store.compositionFacts(new PackIdentifier(location.name())).prompts();
    }

    private static PackContents contentsOf(String name) {
        return new FileSystemPackInspector()
                .inspect(
                        new PackRoot(
                                name,
                                Optional.of(ExamplePacks.level(name)),
                                ExamplePacks.pack(name).toString()),
                        Scope.FULL);
    }

    private static Urn enhancement(String id) {
        return Urn.parse("step_enhancement:" + id).orElseThrow();
    }

    /** The canonical text the full read gives each fragment companion, by its pack-relative path. */
    private static String canonicalFragment(PackContents contents, String path) {
        return contents.stepEnhancements().companions().stream()
                .filter(companion -> companion.path().equals(path))
                .findFirst()
                .orElseThrow()
                .canonicalText();
    }

    @Test
    void theBuiltInPackDeclaresTheReasonsBlockOnTheFiveSlottedStepsAndNothingOnTheOthers() {
        PromptFacts prompts = promptsOf(ExamplePacks.BUILT_IN);

        assertThat(prompts.declared()).isNotEmpty();
        prompts.declared().forEach((action, slots) -> {
            assertThat(action.id()).startsWith("software-dev/");
            String step = action.id().substring("software-dev/".length());
            if (SLOTTED.contains(step)) {
                assertThat(slots).as(action.toString()).containsExactly(new PromptFacts.DeclaredSlot(REASONS, false));
            } else {
                assertThat(slots).as(action.toString()).isEmpty();
            }
        });
        assertThat(prompts.declared().keySet())
                .contains(SLOTTED.stream()
                        .map(step -> Urn.action("software-dev", step))
                        .toArray(Urn[]::new))
                .contains(Urn.action("software-dev", "discovery"), Urn.action("software-dev", "accept"));
    }

    @Test
    void theBuiltInPlanRiskNotesFillTheReasonsBlockWithTheirFragmentsCanonicalText() {
        PromptFacts prompts = promptsOf(ExamplePacks.BUILT_IN);
        String text = canonicalFragment(
                contentsOf(ExamplePacks.BUILT_IN),
                "missions/software-dev/enhancements/plan-risk-notes.prompt-fragment.md");

        assertThat(prompts.fills())
                .containsExactly(
                        Map.entry(enhancement("plan-risk-notes"), List.of(new PromptFacts.Fill(REASONS, text))));
        assertThat(text).contains("list each risk with its likelihood");
    }

    @Test
    void theOrgBasePacksFiveSpddEnhancementsFillTheReasonsBlockWithTheirFragmentText() {
        PromptFacts prompts = promptsOf(ExamplePacks.ORG);
        PackContents contents = contentsOf(ExamplePacks.ORG);

        Map<Urn, List<PromptFacts.Fill>> expected = new LinkedHashMap<>();
        for (String step : List.of("implement", "plan", "review", "specify", "tasks")) {
            String text = canonicalFragment(
                    contents, "missions/software-dev/enhancements/spdd-" + step + ".prompt-fragment.md");
            assertThat(text).as(step).isNotBlank();
            expected.put(enhancement("spdd-" + step), List.of(new PromptFacts.Fill(REASONS, text)));
        }

        assertThat(prompts.fills()).containsExactlyEntriesOf(expected);
        assertThat(prompts.fills().get(enhancement("spdd-plan")).getFirst().text())
                .contains("chosen strategy and rejected alternatives");
        assertThat(prompts.declared()).as("org-base holds no mission type").isEmpty();
    }

    @Test
    void theFactsAgreeWithTheFullReadOfEachExamplePack() {
        for (String name : ExamplePacks.names()) {
            PackContents contents = contentsOf(name);

            Map<String, List<SlotId>> parsed = new LinkedHashMap<>();
            contents.promptSlots().stream()
                    .filter(prompt -> prompt.file().matches("missions/[^/]+/steps/[^/]+\\.prompt\\.md"))
                    .forEach(prompt -> parsed.put(
                            prompt.file(),
                            prompt.parse().slots().stream()
                                    .map(slot -> slot.id())
                                    .toList()));
            Map<String, List<SlotId>> declared = new LinkedHashMap<>();
            promptsOf(name).declared().forEach((action, slots) -> {
                String[] typeAndStep = action.id().split("/", 2);
                declared.put(
                        "missions/" + typeAndStep[0] + "/steps/" + typeAndStep[1] + ".prompt.md",
                        slots.stream().map(PromptFacts.DeclaredSlot::id).toList());
            });

            assertThat(declared).as(name).containsExactlyInAnyOrderEntriesOf(parsed);
            assertThat(promptsOf(name))
                    .as("%s: the facts equal the full read's, canonical text included", name)
                    .isEqualTo(PromptFacts.of(contents.promptSlots(), contents.stepEnhancements()));
        }
    }

    @Test
    void theStagedFactsCarryTheStagedPacksPrompts() throws IOException {
        Path copy = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, scratch);
        Path discovery = copy.resolve("missions/software-dev/steps/discovery.prompt.md");
        Files.writeString(
                discovery,
                Files.readString(discovery, StandardCharsets.UTF_8)
                        + "\n<!-- spdd:reasons-block:start -->\n<!-- spdd:reasons-block:end -->\n",
                StandardCharsets.UTF_8);
        Path fragment = copy.resolve("missions/software-dev/enhancements/plan-risk-notes.prompt-fragment.md");
        Files.writeString(
                fragment,
                Files.readString(fragment, StandardCharsets.UTF_8).replace("list each risk", "list every staged risk"),
                StandardCharsets.UTF_8);

        PromptFacts staged = new WholePackStore(new YamlSerializationStrategy())
                .loadStaged(new StagedPack(copy))
                .facts()
                .prompts();

        assertThat(staged.declared().get(Urn.action("software-dev", "discovery")))
                .as("the staged prompt, not the example's")
                .containsExactly(new PromptFacts.DeclaredSlot(REASONS, false));
        assertThat(staged.fills().get(enhancement("plan-risk-notes")))
                .singleElement()
                .satisfies(fill -> assertThat(fill.text()).contains("list every staged risk"));
        assertThat(promptsOf(ExamplePacks.BUILT_IN).declared().get(Urn.action("software-dev", "discovery")))
                .as("the control: the example as it is declares none there")
                .isEmpty();
    }
}
