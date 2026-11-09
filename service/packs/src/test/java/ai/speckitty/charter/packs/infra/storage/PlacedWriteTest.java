package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.StepEnhancement;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument.Section;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The place-aware write (ADR-0066, A12): a step enhancement cannot be placed by its id, so a create lands where the
 * caller says, with its fragments beside it, and the place is held to what {@code newFile} holds a canonical place to.
 */
@DisplayName("A create at a given place (the place-aware write)")
class PlacedWriteTest {

    private static final String PLACE = "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml";
    private static final String FRAGMENT = "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md";
    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();
    private Path host;
    private Path pack;
    private FilesystemPackStore store;

    @BeforeEach
    void anEmptyPack(@TempDir Path temp) throws IOException {
        host = temp;
        pack = PackFixtures.copyOf("pack-a", temp);
        store = new FilesystemPackStore(temp, strategy);
    }

    private static StepEnhancementDso enhancement(String id, String text) {
        MarkdownDocument body = new MarkdownDocument(Optional.empty(), Optional.empty(), text, List.<Section>of());
        return new StepEnhancementDso(
                id,
                "1.0",
                List.of(new StepEnhancementDso.Entry(
                        "spdd:reasons-block", id + ".prompt-fragment.md", Optional.of(body))));
    }

    private List<String> files() throws IOException {
        try (Stream<Path> walk = Files.walk(pack)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> pack.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    @Test
    @DisplayName("a create lands at the given place, with its fragment beside it, and not at the pack root")
    void aCreateLandsAtTheGivenPlace() throws IOException {
        store.writeAt(PACK, StepEnhancementBinding.INSTANCE, enhancement("spdd-plan", "Fill the slot."), PLACE);

        assertThat(files()).contains(PLACE, FRAGMENT).doesNotContain("spdd-plan.step-enhancement.yaml");
        assertThat(Files.readString(pack.resolve(PLACE))).contains("id: spdd-plan");
        assertThat(Files.readString(pack.resolve(FRAGMENT))).contains("Fill the slot.");
        assertThat(store.readOne(PACK, StepEnhancementBinding.INSTANCE, "spdd-plan"))
                .as("the file is read back as the enhancement it was written as, body included")
                .contains(enhancement("spdd-plan", "Fill the slot."));
    }

    @Test
    @DisplayName(
            "a place that climbs out of the pack, is absolute or is the pack itself is refused, nothing is written")
    void aPlaceOutsideThePackIsRefused() throws IOException {
        List<String> before = files();

        for (String place : List.of(
                "../outside/spdd-plan.step-enhancement.yaml",
                "missions/../../outside/spdd-plan.step-enhancement.yaml",
                "/tmp/spdd-plan.step-enhancement.yaml",
                "missions\\software-dev\\spdd-plan.step-enhancement.yaml",
                "",
                ".")) {
            assertThatThrownBy(() -> store.writeAt(
                            PACK, StepEnhancementBinding.INSTANCE, enhancement("spdd-plan", "Fill the slot."), place))
                    .as(place)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        }

        assertThat(files()).isEqualTo(before);
        assertThat(host.resolve("outside")).doesNotExist();
    }

    @Test
    @DisplayName("a place that is already taken is refused, and the file there is left as it was")
    void aTakenPlaceIsRefused() throws IOException {
        Path taken = pack.resolve("missions/software-dev/enhancements/other-plan.step-enhancement.yaml");
        Files.createDirectories(taken.getParent());
        Files.writeString(taken, "not an enhancement of this id\n");

        assertThatThrownBy(() -> store.writeAt(
                        PACK,
                        StepEnhancementBinding.INSTANCE,
                        enhancement("other-plan", "Fill the slot."),
                        "missions/software-dev/enhancements/other-plan.step-enhancement.yaml"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));

        assertThat(Files.readString(taken)).isEqualTo("not an enhancement of this id\n");
        assertThat(files()).doesNotContain("missions/software-dev/enhancements/other-plan.prompt-fragment.md");
    }

    @Test
    @DisplayName("an id that already exists is replaced where it sits, whatever place is given")
    void anExistingIdIsReplacedInPlace() throws IOException {
        store.writeAt(PACK, StepEnhancementBinding.INSTANCE, enhancement("spdd-plan", "First."), PLACE);

        store.writeAt(
                PACK,
                StepEnhancementBinding.INSTANCE,
                enhancement("spdd-plan", "Second."),
                "elsewhere/spdd-plan.step-enhancement.yaml");

        assertThat(files()).contains(PLACE, FRAGMENT).doesNotContain("elsewhere/spdd-plan.step-enhancement.yaml");
        assertThat(Files.readString(pack.resolve(FRAGMENT))).contains("Second.").doesNotContain("First.");
    }

    @Test
    @DisplayName("a place whose file name is not <id>.step-enhancement.yaml is refused")
    void aPlaceWithAnotherFileNameIsRefused() throws IOException {
        List<String> before = files();

        for (String place : List.of(
                "missions/software-dev/enhancements/another-id.step-enhancement.yaml",
                "missions/software-dev/enhancements/spdd-plan.yaml",
                "missions/software-dev/enhancements/spdd-plan.tactic.yaml",
                "missions/software-dev/enhancements/")) {
            assertThatThrownBy(() -> store.writeAt(
                            PACK, StepEnhancementBinding.INSTANCE, enhancement("spdd-plan", "Fill the slot."), place))
                    .as(place)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        }

        assertThat(files()).isEqualTo(before);
    }

    @Test
    @DisplayName("the repository overload writes a step enhancement at its place and refuses a kind placed by id")
    void theRepositoryOverloadRefusesAKindPlacedById() throws IOException {
        DomainRepositoryService service =
                new DomainRepositoryService(() -> List.of(new PackLocation("pack-a", PackLevel.ORG, pack)), strategy);
        StepEnhancement entity = StepEnhancementBinding.INSTANCE.toEntity(
                new PackIdentifier("01M5DEH8R0VNDTR1PF1XTVRE0A"), enhancement("spdd-plan", "Fill the slot."));

        Paradigm paradigm = ParadigmBinding.INSTANCE.toEntity(
                new PackIdentifier("01M5DEH8R0VNDTR1PF1XTVRE0A"), new ParadigmDso("x", "1.0", "X", "A paradigm."));

        service.saveArtifact(PACK, entity, PLACE);

        assertThat(files()).contains(PLACE, FRAGMENT);
        assertThatThrownBy(() -> service.saveArtifact(PACK, paradigm, "paradigms/x.paradigm.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placed by its id");
        assertThat(files()).doesNotContain("paradigms/x.paradigm.yaml", "x.paradigm.yaml");
    }

    @Test
    @DisplayName("the control: the same enhancement is not writable through the canonical form, whose fileName refuses")
    void theCanonicalFormRefusesIt() {
        assertThatThrownBy(() -> store.write(PACK, StepEnhancementBinding.INSTANCE, enhancement("spdd-plan", "Fill.")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
