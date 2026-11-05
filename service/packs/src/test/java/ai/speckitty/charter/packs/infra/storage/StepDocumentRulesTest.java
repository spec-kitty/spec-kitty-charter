package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.StepDocument;
import ai.speckitty.charter.missions.domain.StepSection;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.domain.markdown.MarkdownRuleCases;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A mission step's prompt and guidelines refuse, when they are built, exactly the texts the {@code markdown} form's
 * line rules refuse (#299): every row of {@link MarkdownRuleCases} is driven through {@link StepDocument} and
 * {@link StepSection}, one field at a time, and through {@link MarkdownDocument}, the model the store writes them as.
 * The two must agree. Every accepted row is written to a mission-type pack by the store and read back equal, so a step
 * text that builds is one the store writes.
 */
@DisplayName("A step's text refuses, when built, what the markdown form's line rules refuse (#299)")
class StepDocumentRulesTest {

    private static final String CASES = "ai.speckitty.charter.packs.domain.markdown.MarkdownRuleCases#";
    private static final PackIdentifier PACK_ID = new PackIdentifier("mission-type");

    private static StepDocument titled(String title) {
        return new StepDocument(Optional.of(title), Optional.empty(), "", List.of());
    }

    private static StepDocument preambled(String preamble) {
        return new StepDocument(Optional.of("T"), Optional.empty(), preamble, List.of());
    }

    /** The model refuses the text, the step refuses it, and the step's breach carries the model's reason. */
    private static void bothRefuse(ThrowingCallable model, String field, ThrowingCallable step) {
        IllegalArgumentException refused = catchThrowableOfType(IllegalArgumentException.class, model);
        assertThat(refused).as("the model refuses it").isNotNull();
        String reason = refused.getMessage().substring(field.length() + 1);
        assertThatThrownBy(step)
                .as("the step refuses it")
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(field)
                .hasMessageEndingWith(reason);
    }

    @Nested
    @DisplayName("refused rows")
    class Refused {

        @ParameterizedTest(name = "title {0}")
        @MethodSource({CASES + "refusedTitles", CASES + "titlesThatDoNotReadBack"})
        @DisplayName("a refused title is refused as the document's title and as a section's title")
        void titles(String title) {
            if (title.isBlank()) {
                // The step document's own blank-title rule runs first and keeps its wording; the model refuses too.
                assertThatThrownBy(() -> new MarkdownDocument(Optional.empty(), Optional.of(title), "", List.of()))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> titled(title))
                        .isInstanceOf(ConstraintBreachException.class)
                        .hasMessageContaining("A step document's title is not blank.");
            } else {
                bothRefuse(
                        () -> new MarkdownDocument(Optional.empty(), Optional.of(title), "", List.of()),
                        "title",
                        () -> titled(title));
            }
            bothRefuse(
                    () -> new MarkdownDocument.Section(title, ""), "section title", () -> new StepSection(title, ""));
        }

        @ParameterizedTest(name = "block {0}")
        @MethodSource(CASES + "refusedBlocks")
        @DisplayName("a refused block is refused as the preamble and as a section's content")
        void blocks(String block) {
            bothRefuse(
                    () -> new MarkdownDocument(Optional.empty(), Optional.of("T"), block, List.of()),
                    "preamble",
                    () -> preambled(block));
            bothRefuse(
                    () -> new MarkdownDocument.Section("S", block),
                    "section content",
                    () -> new StepSection("S", block));
        }

        @ParameterizedTest(name = "opening {0}")
        @MethodSource(CASES + "openingPreambles")
        @DisplayName("an opening preamble is refused with neither title nor description, accepted behind either")
        void openings(String preamble) {
            bothRefuse(
                    () -> new MarkdownDocument(Optional.empty(), Optional.empty(), preamble, List.of()),
                    "preamble",
                    () -> new StepDocument(Optional.empty(), Optional.empty(), preamble, List.of()));
            assertThat(preambled(preamble).preamble()).as("behind a title").isEqualTo(preamble);
            assertThat(new StepDocument(Optional.empty(), Optional.of("Detail."), preamble, List.of()).preamble())
                    .as("behind a description, written as frontmatter")
                    .isEqualTo(preamble);
            assertThat(new MarkdownDocument(
                                    Optional.of(Map.of("description", "Detail.")),
                                    Optional.empty(),
                                    preamble,
                                    List.of())
                            .preamble())
                    .as("the model agrees: frontmatter opens the text")
                    .isEqualTo(preamble);
        }
    }

    @Nested
    @DisplayName("accepted rows build, are written by the store and read back equal")
    class Accepted {

        private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

        @TempDir
        Path host;

        @ParameterizedTest(name = "title {0}")
        @MethodSource(CASES + "acceptedTitles")
        @DisplayName("an accepted title, as the prompt's title and as a section's title")
        void titles(String title) throws IOException {
            roundTrips(new StepDocument(
                    Optional.of(title), Optional.of("Detail."), "", List.of(new StepSection(title, "Body."))));
        }

        @ParameterizedTest(name = "block {0}")
        @MethodSource(CASES + "acceptedBlocks")
        @DisplayName("an accepted block, as the preamble and as a section's content")
        void blocks(String block) throws IOException {
            roundTrips(new StepDocument(
                    Optional.of("Title"), Optional.empty(), block, List.of(new StepSection("Goal", block))));
        }

        @ParameterizedTest(name = "opening {0}")
        @MethodSource(CASES + "openingPreambles")
        @DisplayName("an opening preamble behind a title, and behind a description alone")
        void openings(String preamble) throws IOException {
            roundTrips(new StepDocument(Optional.of("Title"), Optional.empty(), preamble, List.of()));
            roundTrips(new StepDocument(Optional.empty(), Optional.of("Detail."), preamble, List.of()));
        }

        /** Writes the document as the first step's prompt through the store, reads the pack back, compares. */
        private void roundTrips(StepDocument prompt) throws IOException {
            Path pack = host.resolve("mission-type");
            if (!pack.toFile().exists()) {
                PackFixtures.copyOf("mission-type", host);
            }
            FilesystemPackStore store = new FilesystemPackStore(host, strategy);
            MissionType type = MissionTypeBinding.INSTANCE.toEntity(
                    PACK_ID,
                    store.read(PACK_ID, List.of(MissionTypeBinding.INSTANCE))
                            .dsos(MissionTypeBinding.INSTANCE)
                            .getFirst());
            MissionType changed = withFirstPrompt(type, prompt);

            store.write(PACK_ID, MissionTypeBinding.INSTANCE, MissionTypeBinding.INSTANCE.toDso(changed));

            MissionType reread = MissionTypeBinding.INSTANCE.toEntity(
                    PACK_ID,
                    new FilesystemPackStore(host, strategy)
                            .read(PACK_ID, List.of(MissionTypeBinding.INSTANCE))
                            .dsos(MissionTypeBinding.INSTANCE)
                            .getFirst());
            assertThat(reread.steps().getFirst().prompt()).contains(prompt);
        }
    }

    private static MissionType withFirstPrompt(MissionType type, StepDocument prompt) {
        List<MissionStep> steps = new ArrayList<>(type.steps());
        MissionStep first = steps.getFirst();
        steps.set(
                0,
                new MissionStep(
                        first.id(),
                        first.title(),
                        first.description(),
                        first.role(),
                        Optional.of(prompt),
                        first.guidelines(),
                        first.expectedInputArtifacts(),
                        first.expectedOutputArtifacts(),
                        first.references()));
        MissionType.Builder builder = MissionType.builder()
                .id(type.id())
                .packId(type.packId())
                .schemaVersion(type.schemaVersion().orElseThrow())
                .title(type.title())
                .description(type.description().orElseThrow())
                .content(type.content().orElse(null))
                .requiresSubstantiveSpec(type.requiresSubstantiveSpec())
                .pathConventions(type.pathConventions().orElse(null));
        type.expectedOutputArtifacts().forEach(builder::expectedOutputArtifact);
        type.references().forEach(builder::reference);
        steps.forEach(builder::step);
        type.onDemandSteps().forEach(builder::onDemandStep);
        return builder.build();
    }
}
