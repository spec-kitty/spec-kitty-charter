package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.domain.ArtifactClass;
import ai.speckitty.charter.missions.domain.MissionReference;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.StepDocument;
import ai.speckitty.charter.missions.domain.StepSection;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The mission type in the pack store (ADR-0044): one file per type at {@code missions/<id>/<id>.mission-type.yaml}, its
 * steps as sub-items, each step's prompt and guidelines as Markdown companions at their derived paths. Each refusal
 * stands beside its accepted neighbour, and the positive controls run through the production write path.
 */
@DisplayName("A mission type round-trips from one file with its step companions (#194)")
class MissionTypeBindingTest {

    private static final String PACK = "mission-type";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);
    private static final String FILE = "missions/research/research.mission-type.yaml";
    private static final String PROMPT = "missions/research/steps/scoping.prompt.md";
    private static final String GUIDELINES = "missions/research/steps/scoping.guidelines.md";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @TempDir
    Path host;

    private FilesystemPackStore.PackRead read() {
        return new FilesystemPackStore(host, strategy).read(PACK_ID, List.of(MissionTypeBinding.INSTANCE));
    }

    private Pack load(Path pack) {
        return new DomainRepositoryService(() -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), strategy)
                .load(PACK_ID);
    }

    private static MissionType missionType(Pack pack) {
        return pack.artifacts().stream()
                .filter(MissionType.class::isInstance)
                .map(MissionType.class::cast)
                .findFirst()
                .orElseThrow();
    }

    // ---- the fixture round trip ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the fixture reads into a mission type with its steps in order, documents and side-kept artifacts")
    void theFixtureReadsIntoTheDomain() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);

        Pack loaded = load(pack);

        assertThat(loaded.violations()).isEmpty();
        MissionType type = missionType(loaded);
        assertThat(type.kind()).isEqualTo(ArtifactKind.MISSION_TYPE);
        assertThat(type.steps()).extracting(MissionStep::id).containsExactly("scoping", "methodology");
        assertThat(type.onDemandSteps()).extracting(MissionStep::id).containsExactly("retrospect");
        MissionStep scoping = type.steps().getFirst();
        assertThat(scoping.role()).contains("researcher");
        assertThat(scoping.expectedInputArtifacts())
                .singleElement()
                .satisfies(input -> assertThat(input.artifactKey()).isEqualTo("input.charter.main"));
        assertThat(scoping.expectedOutputArtifacts()).singleElement().satisfies(output -> {
            assertThat(output.blocking()).isTrue();
            assertThat(output.artifactClass()).isEqualTo(ArtifactClass.OUTPUT);
        });
        StepDocument prompt = scoping.prompt().orElseThrow();
        assertThat(prompt.title()).contains("Research Scoping");
        assertThat(prompt.sections()).extracting(StepSection::title).containsExactly("Goal", "Steps");
        assertThat(prompt.sections().get(1).content())
                .as("a heading in a fence stays content")
                .contains("## not");
        assertThat(type.steps().get(1).prompt().orElseThrow().title())
                .as("a prompt without an H1")
                .isEmpty();
        assertThat(type.steps().get(1).guidelines()).isEmpty();
        assertThat(scoping.references())
                .extracting(MissionReference::id)
                .containsExactly("requirements-validation-workflow", "DIRECTIVE_003");
        assertThat(loaded.artifacts())
                .flatExtracting(CharterArtifact::relationships)
                .isEmpty();
    }

    @Test
    @DisplayName("the file and its companions write back byte for byte through the production writer")
    void theFixtureWritesBackByteForByte() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Map<String, String> source = new TreeMap<>();
        for (String file : List.of(FILE, PROMPT, GUIDELINES, "missions/research/steps/methodology.prompt.md")) {
            source.put(file, Files.readString(pack.resolve(file)));
        }
        MissionTypeDso dso = read().dsos(MissionTypeBinding.INSTANCE).getFirst();
        MissionType type = MissionTypeBinding.INSTANCE.toEntity(PACK_ID, dso);
        assertThat(MissionTypeBinding.INSTANCE.toDso(type)).as("DSO fidelity").isEqualTo(dso);

        for (String file : source.keySet()) {
            Files.delete(pack.resolve(file));
        }
        new FilesystemPackStore(host, strategy)
                .write(PACK_ID, MissionTypeBinding.INSTANCE, MissionTypeBinding.INSTANCE.toDso(type));

        source.forEach((file, text) -> assertThat(pack.resolve(file)).as(file).hasContent(text));
    }

    @Test
    @DisplayName("a new mission type is created at missions/<id>/ with its steps' companions, directories included")
    void aNewMissionTypeIsCreatedAtItsPlace() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        MissionTypeDso fixture = read().dsos(MissionTypeBinding.INSTANCE).getFirst();
        MissionTypeDso renamed = new MissionTypeDso(
                "plan",
                fixture.schemaVersion(),
                "Plan",
                fixture.description(),
                Optional.empty(),
                false,
                Map.of(),
                List.of(),
                List.of(),
                fixture.steps(),
                List.of());

        new FilesystemPackStore(host, strategy).write(PACK_ID, MissionTypeBinding.INSTANCE, renamed);

        assertThat(pack.resolve("missions/plan/plan.mission-type.yaml")).exists();
        assertThat(pack.resolve("missions/plan/steps/scoping.prompt.md")).hasSameTextualContentAs(pack.resolve(PROMPT));
        assertThat(read().dsos(MissionTypeBinding.INSTANCE))
                .extracting(MissionTypeDso::id)
                .contains("plan");
    }

    @ParameterizedTest(name = "the id ''{0}'' does not name a place in the pack")
    @ValueSource(strings = {"../escape", "a/b", "a\\b"})
    void anIdThatLeavesItsPlaceIsRefusedOnCreate(String id) throws IOException {
        PackFixtures.copyOf(PACK, host);
        MissionTypeDso fixture = read().dsos(MissionTypeBinding.INSTANCE).getFirst();
        MissionTypeDso escaping = new MissionTypeDso(
                id, "1.0", "T", "D", Optional.empty(), false, Map.of(), List.of(), List.of(), List.of(), List.of());
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        assertThatThrownBy(() -> store.write(PACK_ID, MissionTypeBinding.INSTANCE, escaping))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("mission_type id '" + id + "' does not name a file in the pack");

        // The accepted neighbour: the same shape under a valid new id is written at its place and reads back.
        MissionTypeDso neighbour = new MissionTypeDso(
                "neighbour",
                "1.0",
                "T",
                "D",
                Optional.empty(),
                false,
                Map.of(),
                List.of(),
                List.of(),
                fixture.steps(),
                List.of());
        store.write(PACK_ID, MissionTypeBinding.INSTANCE, neighbour);
        assertThat(read().dsos(MissionTypeBinding.INSTANCE))
                .extracting(MissionTypeDso::id)
                .containsExactlyInAnyOrder("research", "neighbour");
    }

    // ---- placement and derived paths ----------------------------------------------------------------------------

    @ParameterizedTest(name = "a mission type at {0} is refused; at its place it is read")
    @ValueSource(
            strings = {
                "research.mission-type.yaml",
                "mission_types/research.mission-type.yaml",
                "missions/research/nested/research.mission-type.yaml",
                "missions/other/research.mission-type.yaml"
            })
    void aMisplacedMissionTypeIsRefused(String place) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Path target = pack.resolve(place);
        Files.createDirectories(target.getParent());
        Files.move(pack.resolve(FILE), target);

        FilesystemPackStore.PackRead read = read();

        assertThat(read.dsos(MissionTypeBinding.INSTANCE)).isEmpty();
        assertThat(read.violations())
                .extracting(PackElementViolation::reason)
                .contains("mission_type 'research' is not at its place '" + FILE + "'");
    }

    @Test
    @DisplayName("validate mode refuses a misplaced mission type as a const violation at its id")
    void validateModeRefusesAMisplacedMissionType() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Path target = pack.resolve("missions/other/research.mission-type.yaml");
        Files.createDirectories(target.getParent());
        Files.move(pack.resolve(FILE), target);

        PackContents validated = new FileSystemPackInspector()
                .inspect(new PackRoot(PACK, Optional.of(PackLevel.ORG), pack.toString()), PackInspector.Scope.FULL);

        assertThat(validated.unreadableFiles())
                .extracting(FileRead::file)
                .containsExactly("missions/other/research.mission-type.yaml");
    }

    @Test
    @DisplayName("an output template that names a file is read; one that is missing or leaves the pack is refused")
    void outputTemplatesNameAFile() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        String withTemplate = Files.readString(pack.resolve(FILE))
                .replace(
                        "    path_pattern: spec.md\n    blocking: true\n",
                        "    path_pattern: spec.md\n    blocking: true\n    template_path: templates/spec-template.md\n");
        Files.writeString(pack.resolve(FILE), withTemplate);

        assertThat(read().violations())
                .containsExactly(new PackElementViolation(
                        FILE, "reference", "templates/spec-template.md (named by step 'scoping'): missing"));

        Files.createDirectories(pack.resolve("missions/research/templates"));
        Files.writeString(pack.resolve("missions/research/templates/spec-template.md"), "# Spec\n");
        assertThat(read().violations()).isEmpty();
        assertThat(read().dsos(MissionTypeBinding.INSTANCE)).hasSize(1);

        // A templates directory that links out of the pack: the file is there, its real place is not in the pack.
        Path outside = Files.createDirectories(host.resolve("outside"));
        Files.writeString(outside.resolve("spec-template.md"), "# Spec\n");
        Path templates = pack.resolve("missions/research/templates");
        Files.delete(templates.resolve("spec-template.md"));
        Files.delete(templates);
        Files.createSymbolicLink(templates, outside);
        assertThat(read().violations())
                .extracting(PackElementViolation::reason)
                .containsExactly("templates/spec-template.md (named by step 'scoping'): leaves the pack");
        Files.delete(templates);

        Files.writeString(
                pack.resolve(FILE), withTemplate.replace("templates/spec-template.md", "templates/../../../x.md"));
        assertThat(read().violations())
                .extracting(PackElementViolation::reason)
                .as("the schema refuses a template path that climbs, before the store looks for it")
                .containsExactly("not violated at /steps/0/expected_output_artifacts/0/template_path");
    }

    @Test
    @DisplayName(
            "an output template must be a template of the pack: an excluded file, a directory or no file is refused;"
                    + " a link inside the pack is followed")
    void anOutputTemplateIsATemplateOfThePack() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        String withTemplate = Files.readString(pack.resolve(FILE))
                .replace(
                        "    path_pattern: spec.md\n    blocking: true\n",
                        "    path_pattern: spec.md\n    blocking: true\n    template_path: templates/spec-template.md\n");
        Path templates = Files.createDirectories(pack.resolve("missions/research/templates"));
        Files.writeString(templates.resolve("real.md"), "# Spec\n");

        Files.writeString(pack.resolve(FILE), withTemplate.replace("spec-template.md", "README.md"));
        Files.writeString(templates.resolve("README.md"), "notes\n");
        assertThat(read().violations())
                .extracting(PackElementViolation::reason)
                .as("an excluded README is no template, so it is not digested and cannot be named")
                .containsExactly("templates/README.md (named by step 'scoping'): is not a template of the pack");

        Files.writeString(pack.resolve(FILE), withTemplate.replace("spec-template.md", "folder"));
        Files.createDirectories(templates.resolve("folder"));
        assertThat(read().violations())
                .extracting(PackElementViolation::reason)
                .containsExactly("templates/folder (named by step 'scoping'): is not a regular file");

        Files.writeString(pack.resolve(FILE), withTemplate);
        Files.createSymbolicLink(templates.resolve("spec-template.md"), templates.resolve("real.md"));
        assertThat(read().violations())
                .as("a link whose real place is a template of the pack")
                .isEmpty();
    }

    @Test
    @DisplayName("a write that names an output template the pack does not hold is refused before anything is written")
    void aWriteNamingNoTemplateIsRefused() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        String withTemplate = Files.readString(pack.resolve(FILE))
                .replace(
                        "    path_pattern: spec.md\n    blocking: true\n",
                        "    path_pattern: spec.md\n    blocking: true\n    template_path: templates/spec-template.md\n");
        Files.writeString(pack.resolve(FILE), withTemplate);
        Files.createDirectories(pack.resolve("missions/research/templates"));
        Files.writeString(pack.resolve("missions/research/templates/spec-template.md"), "# Spec\n");
        MissionTypeDso held = read().dsos(MissionTypeBinding.INSTANCE).getFirst();
        Files.delete(pack.resolve("missions/research/templates/spec-template.md"));
        String before = Files.readString(pack.resolve(FILE));
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        assertThatThrownBy(() -> store.write(PACK_ID, MissionTypeBinding.INSTANCE, held))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("mission_type 'research' names templates/spec-template.md (named by step 'scoping'):"
                        + " missing");
        assertThat(pack.resolve(FILE)).hasContent(before);
    }

    /** The fixture with its scoping step's output naming {@code templates/spec-template.md}, which it holds. */
    private Path withSpecTemplate() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(
                pack.resolve(FILE),
                Files.readString(pack.resolve(FILE))
                        .replace(
                                "    path_pattern: spec.md\n    blocking: true\n",
                                "    path_pattern: spec.md\n    blocking: true\n    template_path:"
                                        + " templates/spec-template.md\n"));
        Files.createDirectories(pack.resolve("missions/research/templates"));
        Files.writeString(pack.resolve("missions/research/templates/spec-template.md"), "# Spec\n");
        return pack;
    }

    private static final String UNRESOLVED =
            "templates/spec-template.md (named by step 'scoping'): is not a template of the type";

    @Test
    @DisplayName("#277: an output template resolves to the type's own template when the pack is assembled; a template"
            + " of the same id outside the type's templates/, or none, refuses the type")
    void anOutputTemplateResolvesToTheTypesOwnTemplate() throws IOException {
        withSpecTemplate();
        FilesystemPackStore.PackRead read = read();
        FilesystemPackStore.TemplateFile own = read.templates().stream()
                .filter(file -> file.place().equals("missions/research/templates/spec-template.md"))
                .findFirst()
                .orElseThrow();

        Pack resolved =
                DomainRepositoryService.assembly(PACK_ID, read, new HashMap<>()).build();
        assertThat(resolved.violations()).isEmpty();
        assertThat(missionType(resolved).templateReferences())
                .extracting(
                        reference -> reference.templateId(missionType(resolved).id()))
                .containsExactly("research/spec-template.md");

        for (List<FilesystemPackStore.TemplateFile> templates : List.of(
                List.of(new FilesystemPackStore.TemplateFile(
                        own.path(), own.content(), "templates/research/spec-template.md")),
                List.<FilesystemPackStore.TemplateFile>of())) {
            Pack refused = DomainRepositoryService.assembly(
                            PACK_ID,
                            new FilesystemPackStore.PackRead(
                                    read.descriptor(),
                                    read.elements(),
                                    read.violations(),
                                    read.assetFiles(),
                                    templates,
                                    read.presets(),
                                    read.document()),
                            new HashMap<>())
                    .build();
            assertThat(refused.artifacts(ArtifactKind.MISSION_TYPE))
                    .as("%s", templates)
                    .isEmpty();
            assertThat(refused.violations())
                    .as("%s", templates)
                    .containsExactly(new PackElementViolation(FILE, "reference", UNRESOLVED));
        }
    }

    @Test
    @DisplayName("#277: a whole-pack write whose type names no template of the aggregate is refused before anything"
            + " is written; the type's own template writes")
    void aWholePackWriteNamingNoTemplateOfTheAggregateIsRefused() throws IOException {
        Path pack = withSpecTemplate();
        WholePackStore store = new WholePackStore(strategy);
        WholePackStore.LoadedPack loaded = store.load(pack);
        Template own = loaded.pack().templates().stream()
                .filter(template -> template.id().value().equals("research/spec-template.md"))
                .findFirst()
                .orElseThrow();
        // The same id, filed under the pack's top-level templates/: the write would put it at
        // templates/research/spec-template.md, where the type's path does not lead.
        Template topLevel = Template.builder()
                .id(own.id())
                .packId(own.packId())
                .content(own.content())
                .build();
        WholePackStore.LoadedPack moved = new WholePackStore.LoadedPack(
                loaded.pack().with(topLevel), loaded.layout(), loaded.graphs(), loaded.unmodelled(), loaded.manifest());
        Path refused = host.resolve("refused").resolve(PACK);

        Path clean = host.resolve("clean").resolve(PACK);
        store.write(loaded, clean);
        assertThat(clean.resolve(FILE)).as("the path is kept as written").hasSameBinaryContentAs(pack.resolve(FILE));

        assertThatThrownBy(() -> store.write(moved, refused)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
            assertThat(refusal.getMessage()).isEqualTo("mission_type 'research' names " + UNRESOLVED);
        });
        assertThat(refused).doesNotExist();
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '~',
            value = {
                "- id: retrospect | - id: scoping | step 'scoping' stands twice across steps and on_demand_steps",
                "prompt_path: steps/methodology.prompt.md | prompt_path: steps/scoping.prompt.md"
                        + " | step 'methodology' names steps/scoping.prompt.md, not its derived path"
                        + " steps/methodology.prompt.md",
                "prompt_path: steps/methodology.prompt.md | prompt_path: steps/methodology.prompt.md\\n"
                        + "  expected_output_artifacts:\\n  - artifact_key: output.method.research\\n"
                        + "    artifact_class: output\\n    path_pattern: method.md\\n"
                        + "    template_path: templates/nowhere.md"
                        + " | names templates/nowhere.md (named by step 'methodology'): missing",
            })
    @DisplayName("validate mode refuses what the load refuses of a schema-valid mission type, naming why")
    void validateModeRefusesWhatTheLoadRefuses(String from, String to, String refusal) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(
                pack.resolve(FILE), Files.readString(pack.resolve(FILE)).replace(from, to.replace("\\n", "\n")));

        PackContents validated = new FileSystemPackInspector()
                .inspect(new PackRoot(PACK, Optional.of(PackLevel.ORG), pack.toString()), PackInspector.Scope.FULL);

        assertThat(validated.unreadableFiles())
                .singleElement()
                .satisfies(bad -> assertThat(PackPolicy.unreadable(PACK, bad))
                        .extracting(PackFinding::detail)
                        .containsExactly(PACK + "/" + FILE + " " + refusal));
        // The store refuses by file; the domain (a step id standing twice) by the type's id. The two paths
        // detect the one defect through their own checks, so their wording differs by design — parity is that
        // the load raises exactly one violation, on the same file, with a stated reason (not that it is empty).
        assertThat(load(pack).violations())
                .as("the load refuses the same mission type with a single stated violation")
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.file()).isIn(FILE, "research");
                    assertThat(violation.reason()).isNotBlank();
                });
    }

    @Test
    @DisplayName("validate mode refuses a mission type whose step names a prompt that is not there, as the load does")
    void validateModeRefusesAMissingPrompt() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.delete(pack.resolve(PROMPT));

        PackContents validated = new FileSystemPackInspector()
                .inspect(new PackRoot(PACK, Optional.of(PackLevel.ORG), pack.toString()), PackInspector.Scope.FULL);

        assertThat(validated.unreadableFiles())
                .singleElement()
                .satisfies(bad -> assertThat(PackPolicy.unreadable(PACK, bad))
                        .extracting(PackFinding::detail)
                        .containsExactly(PACK + "/" + FILE + " step 'scoping' names steps/scoping.prompt.md: missing"));
        assertThat(load(pack).violations())
                .extracting(PackElementViolation::file)
                .containsOnly(FILE);
    }

    @Test
    @DisplayName("a prompt_path other than the derived one is refused when the pack loads; the derived one is read")
    void aNonDerivedPromptPathIsRefused() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.move(
                pack.resolve("missions/research/steps/methodology.prompt.md"),
                pack.resolve("missions/research/steps/method.prompt.md"));
        Files.writeString(
                pack.resolve(FILE),
                Files.readString(pack.resolve(FILE)).replace("steps/methodology.prompt.md", "steps/method.prompt.md"));

        Pack loaded = load(pack);

        assertThat(loaded.violations())
                .extracting(PackElementViolation::reason)
                .contains("mission type 'research' step 'methodology': names steps/method.prompt.md, not its derived"
                        + " path steps/methodology.prompt.md");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "owner: me\ndescription: Choose.",
                "description: 3",
                "description: ''",
                "description: ' '",
                "description: [a]",
                "owner: me",
                ""
            })
    @DisplayName("a prompt frontmatter other than one description text is refused on the prompt")
    void anyOtherPromptFrontmatterIsRefused(String frontmatter) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Path methodology = pack.resolve("missions/research/steps/methodology.prompt.md");
        String held = Files.readString(methodology);
        String body = held.substring(held.indexOf("\n---\n", 4) + 5);
        Files.writeString(methodology, "---\n" + (frontmatter.isEmpty() ? "" : frontmatter + "\n") + "---\n" + body);

        assertThat(read().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(FILE);
            assertThat(violation.reason()).startsWith("steps/methodology.prompt.md: ");
        });
    }

    @Test
    @DisplayName("a prompt's frontmatter is its detailed description; guidelines frontmatter is refused")
    void promptFrontmatterIsTheDetailedDescription() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);

        MissionStep step = MissionTypeBinding.INSTANCE
                .toEntity(PACK_ID, read().dsos(MissionTypeBinding.INSTANCE).getFirst())
                .steps()
                .get(1);
        assertThat(step.prompt().orElseThrow().description())
                .contains("Choose a research method that fits the question, weigh the alternatives, and record the"
                        + " reasons for the choice.");

        Files.writeString(
                pack.resolve(GUIDELINES),
                "---\ndescription: Scope it\n---\n" + Files.readString(pack.resolve(GUIDELINES)));
        assertThat(read().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(FILE);
            assertThat(violation.reason())
                    .startsWith("steps/scoping.guidelines.md: ")
                    .contains("frontmatter");
        });
    }

    @Test
    @DisplayName("a missing prompt is a companion violation of its mission type")
    void aMissingPromptIsAViolation() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.delete(pack.resolve(PROMPT));

        FilesystemPackStore.PackRead read = read();

        assertThat(read.dsos(MissionTypeBinding.INSTANCE)).isEmpty();
        assertThat(read.violations())
                .containsExactly(new PackElementViolation(FILE, "companion", "steps/scoping.prompt.md: missing"));
    }

    // ---- the gate -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the fixture pack round-trips through the gate green, every companion paired")
    void theFixtureIsGreenThroughTheGate() throws IOException {
        Path source = PackFixtures.copyOf(PACK, host);

        Gate faithful = gate(source, "faithful", pack -> pack);

        assertThat(faithful.observed())
                .containsKeys(FILE, PROMPT, GUIDELINES)
                .allSatisfy((path, red) -> assertThat(red).as(path).isEmpty());
        assertThat(faithful.unpaired()).isEmpty();
    }

    @Test
    @DisplayName("positive control: a changed step title is a difference in the mission-type file")
    void aChangedStepTitleIsReported() throws IOException {
        Gate changed = gate(
                PackFixtures.copyOf(PACK, host),
                "title",
                pack -> pack.with(edit(pack, type -> {
                    List<MissionStep> steps = new ArrayList<>(type.steps());
                    MissionStep first = steps.getFirst();
                    steps.set(0, retitled(first, "Scoping, renamed"));
                    return rebuilt(type, steps, type.onDemandSteps());
                })));

        assertThat(changed.observed().get(FILE)).isPresent();
    }

    @Test
    @DisplayName("positive control: two steps swapped are a difference, so order is meaning")
    void swappedStepsAreReported() throws IOException {
        Gate changed = gate(
                PackFixtures.copyOf(PACK, host),
                "swapped",
                pack -> pack.with(edit(
                        pack,
                        type -> rebuilt(
                                type, List.of(type.steps().get(1), type.steps().get(0)), type.onDemandSteps()))));

        assertThat(changed.observed().get(FILE)).isPresent();
    }

    @Test
    @DisplayName("positive control: a dropped on-demand step is a difference")
    void aDroppedOnDemandStepIsReported() throws IOException {
        Gate changed = gate(
                PackFixtures.copyOf(PACK, host),
                "dropped",
                pack -> pack.with(edit(pack, type -> rebuilt(type, type.steps(), List.of()))));

        assertThat(changed.observed().get(FILE)).isPresent();
    }

    @Test
    @DisplayName("positive control: an edited prompt section is a difference at that section, the file unchanged")
    void anEditedPromptSectionIsReported() throws IOException {
        Gate changed = gate(
                PackFixtures.copyOf(PACK, host),
                "prompt",
                pack -> pack.with(edit(pack, type -> {
                    List<MissionStep> steps = new ArrayList<>(type.steps());
                    MissionStep first = steps.getFirst();
                    StepDocument prompt = first.prompt().orElseThrow();
                    List<StepSection> sections = new ArrayList<>(prompt.sections());
                    sections.set(
                            0,
                            new StepSection(
                                    "Goal", sections.getFirst().content().replace("question", "questioN")));
                    steps.set(
                            0,
                            new MissionStep(
                                    first.id(),
                                    first.title(),
                                    first.description(),
                                    first.role(),
                                    Optional.of(new StepDocument(
                                            prompt.title(), prompt.description(), prompt.preamble(), sections)),
                                    first.guidelines(),
                                    first.expectedInputArtifacts(),
                                    first.expectedOutputArtifacts(),
                                    first.references()));
                    return rebuilt(type, steps, type.onDemandSteps());
                })));

        assertThat(changed.observed().get(PROMPT))
                .hasValueSatisfying(red -> assertThat(red).startsWith("sections[0].content: "));
        assertThat(changed.observed().get(FILE)).as("the mission-type file").isEmpty();
    }

    @Test
    @DisplayName("positive control: reordered references are a difference")
    void reorderedReferencesAreReported() throws IOException {
        Gate changed = gate(
                PackFixtures.copyOf(PACK, host),
                "references",
                pack -> pack.with(edit(pack, type -> {
                    List<MissionStep> steps = new ArrayList<>(type.steps());
                    MissionStep first = steps.getFirst();
                    steps.set(
                            0,
                            new MissionStep(
                                    first.id(),
                                    first.title(),
                                    first.description(),
                                    first.role(),
                                    first.prompt(),
                                    first.guidelines(),
                                    first.expectedInputArtifacts(),
                                    first.expectedOutputArtifacts(),
                                    List.of(
                                            first.references().get(1),
                                            first.references().get(0))));
                    return rebuilt(type, steps, type.onDemandSteps());
                })));

        assertThat(changed.observed().get(FILE)).isPresent();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private record Gate(Map<String, Optional<String>> observed, List<String> unpaired) {}

    private Gate gate(Path source, String run, UnaryOperator<Pack> beforeSave) {
        Path target = host.resolve(run).resolve(PACK);
        SortedMap<String, String> census = WholePackCensus.of(source);
        Map<String, Outcome> outcomes = new TreeMap<>();
        WholePackRoundTrip recording = new WholePackRoundTrip() {
            private final DomainRoundTrip domain = new DomainRoundTrip(strategy, beforeSave);

            @Override
            public Set<String> modelledKinds() {
                return domain.modelledKinds();
            }

            @Override
            public Map<String, Outcome> roundTrip(Path from, Path to, Map<String, String> kindOf) {
                outcomes.putAll(domain.roundTrip(from, to, kindOf));
                return outcomes;
            }
        };
        Set<String> paired = new TreeSet<>();
        Map<String, Optional<String>> observed = WholePackRoundTripTest.observe(
                recording, source, target, census, new TreeMap<>(), paired, WholePackCensus.formsOf(source));
        return new Gate(observed, WholePackRoundTripTest.unpaired(target, paired));
    }

    private static MissionType edit(Pack pack, UnaryOperator<MissionType> change) {
        return change.apply(missionType(pack));
    }

    private static MissionStep retitled(MissionStep step, String title) {
        return new MissionStep(
                step.id(),
                title,
                step.description(),
                step.role(),
                step.prompt(),
                step.guidelines(),
                step.expectedInputArtifacts(),
                step.expectedOutputArtifacts(),
                step.references());
    }

    private static MissionType rebuilt(MissionType type, List<MissionStep> steps, List<MissionStep> onDemand) {
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
        onDemand.forEach(builder::onDemandStep);
        return builder.build();
    }
}
