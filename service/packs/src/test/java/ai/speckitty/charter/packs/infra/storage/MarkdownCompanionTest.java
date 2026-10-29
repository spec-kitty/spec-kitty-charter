package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Skill;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Written;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Markdown companions (#194, ADR-0039 widened): a companion declares its form, the store reads, validates and writes
 * it by that form, a companion never claims a file another rule classifies (except at its own rule), and the gate
 * pairs and compares companions by form. Proved with a test binding, {@link MarkdownSkill}: the skill kind whose body
 * is a Markdown companion validated by the {@code notes} test structure schema, read and written through the real YAML
 * strategy. The bundled table gains one test rule, {@code *}{@code /notes/*.md}, the {@code notes} schema's own.
 */
@DisplayName("Markdown companions are read, validated, written and compared by their declared form (#194)")
class MarkdownCompanionTest {

    private static final String FIXTURES = "markdown-rule-fixtures/";
    private static final String RULE = "  - match: \"*/notes/*.md\"\n    schema: notes\n    content_form: markdown\n";
    private static final Companions.Rules RULES = new Companions.Rules(
            new PackFileKind(withTestRule(resource(PackFileKind.RESOURCE)), FIXTURES),
            () -> new PackSchemas(FIXTURES, List.of("notes")));
    /** The registry with the skill binding replaced by the test binding. */
    private static final List<KindBinding<?, ?>> BINDINGS = KindBindings.all().stream()
            .<KindBinding<?, ?>>map(binding -> binding.kind() == ArtifactKind.SKILL ? MarkdownSkill.INSTANCE : binding)
            .toList();

    private static final String PACK = "built-in";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);
    private static final String PACK_YAML = """
            pack_id: 01ARWG13C000000000000000FG
            pack_version: 1.0.0
            name: built-in
            pack_level: built-in
            """;
    private static final String SKILL_FILE = "skills/land.skill.yaml";
    private static final String BODY_FILE = "skills/land.md";
    /** A body the {@code notes} schema accepts, in the canonical form. */
    private static final String BODY = """
            ---
            id: land
            ---
            # Landing a pull request

            Read this before merging.

            ## Summary

            Rebase, then merge.

            ## Detail

            Keep the history linear.
            """;

    @TempDir
    Path host;

    // ---- T021: a companion declares its form ------------------------------------------------------------------------

    @Test
    @DisplayName("a companion is text with no structure schema or markdown with one, holding content of its form")
    void aCompanionDeclaresAnAdmittedForm() {
        Optional<CompanionContent> text = Optional.of(new CompanionContent.Text("x"));

        assertThat(new KindBinding.Companion("a.md", text))
                .as("the text shorthand the skill tests use")
                .isEqualTo(new KindBinding.Companion("a.md", text, ContentForm.TEXT, Optional.empty()));
        assertThatThrownBy(() -> new KindBinding.Companion("a.md", text, ContentForm.MARKDOWN, Optional.of("notes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("companion a.md is declared markdown but holds text");
        assertThatThrownBy(() ->
                        new KindBinding.Companion("a.md", Optional.empty(), ContentForm.MARKDOWN, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new KindBinding.Companion("a.md", Optional.empty(), ContentForm.TEXT, Optional.of("notes")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new KindBinding.Companion("a.md", Optional.empty(), ContentForm.YAML, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SkillBinding.INSTANCE.companions(dso("land", "land.md", BODY)))
                .as("the skill body is a text companion, as before")
                .containsExactly(new KindBinding.Companion(
                        "land.md", Optional.of(new CompanionContent.Text(BODY)), ContentForm.TEXT, Optional.empty()));
    }

    // ---- T023: the store reads, validates and writes by form --------------------------------------------------------

    @Test
    @DisplayName("a Markdown companion is read into its model and written back in the canonical form")
    void aMarkdownCompanionReadsAndWritesCanonically() throws IOException {
        Path pack = pack(BODY.replace("## Detail", "\n\n## Detail"));
        FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy(), RULES);

        List<SkillDso> read = store.readAll(PACK_ID, MarkdownSkill.INSTANCE);

        assertThat(read).extracting(SkillDso::body).containsExactly(Optional.of(BODY));
        store.write(PACK_ID, MarkdownSkill.INSTANCE, read.getFirst());
        assertThat(pack.resolve(BODY_FILE)).hasContent(BODY);
        assertThat(store.readOne(PACK_ID, MarkdownSkill.INSTANCE, "land")).contains(read.getFirst());
    }

    @Test
    @DisplayName("a Markdown refusal or a structure breach is a companion violation naming the path, line and reason")
    void aRefusedMarkdownCompanionIsAViolation() throws IOException {
        Path pack = pack(BODY.replace("# Landing a pull request\n", "# Landing a pull request\r\n"));
        write(pack, "skills/broken.skill.yaml", skill("broken", "broken.md"));
        write(pack, "skills/broken.md", BODY.replace("## Summary", "## Overview"));
        FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy(), RULES);

        FilesystemPackStore.PackRead read = store.read(PACK_ID, List.of(MarkdownSkill.INSTANCE));

        assertThat(read.dsos(MarkdownSkill.INSTANCE)).isEmpty();
        assertThat(read.violations())
                .containsExactlyInAnyOrder(
                        new PackElementViolation(
                                "skills/broken.skill.yaml",
                                "companion",
                                "broken.md: structure: minContains violated at /sections"),
                        new PackElementViolation(
                                SKILL_FILE, "companion", "land.md: line 4: a carriage return is not admitted"));
    }

    @Test
    @DisplayName("a Markdown companion breaking its structure schema is refused as a client error before any write")
    void aStructureBreachIsRefusedOnWrite() throws IOException {
        Path pack = Files.createDirectories(host.resolve(PACK));
        write(pack, "pack.yaml", PACK_YAML);
        FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy(), RULES);
        SkillDso dso = dso("land", "land.md", BODY.replace("## Summary", "## Overview"));

        assertThatThrownBy(() -> store.write(PACK_ID, MarkdownSkill.INSTANCE, dso))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .hasMessage("skill 'land': companion land.md breaks its structure: minContains violated at /sections");
        assertThat(pack.resolve("land.md")).doesNotExist();
        assertThat(pack.resolve("land.skill.yaml")).doesNotExist();
    }

    @Test
    @DisplayName("a companion-only change moves the fingerprint, even while the companion's structure is refused")
    void aCompanionOnlyChangeMovesTheFingerprint() throws IOException {
        Path pack = pack(BODY);
        List<PackLocation> locations = List.of(new PackLocation(PACK, PackLevel.BUILT_IN, pack));
        Function<Path, List<String>> filesOf = directory -> {
            List<String> files = new ArrayList<>(List.of("pack.yaml", SKILL_FILE));
            files.addAll(PackFileSet.companions(directory, SKILL_FILE, MarkdownSkill.INSTANCE, RULES));
            return files;
        };
        assertThat(filesOf.apply(pack.toAbsolutePath().normalize()))
                .containsExactly("pack.yaml", SKILL_FILE, BODY_FILE);
        String before = FileSystemPackSources.digest(locations, filesOf);

        write(pack, BODY_FILE, BODY.replace("Rebase, then merge.", "Rebase, then merge!"));
        String changed = FileSystemPackSources.digest(locations, filesOf);
        write(pack, BODY_FILE, BODY.replace("## Summary", "## Overview"));
        String breached = FileSystemPackSources.digest(locations, filesOf);

        assertThat(changed).isNotEqualTo(before);
        assertThat(breached).isNotEqualTo(changed).isNotEqualTo(before);
        assertThat(filesOf.apply(pack.toAbsolutePath().normalize()))
                .as("the refused companion is still digested")
                .contains(BODY_FILE);
    }

    // ---- T022: one file, one owner ----------------------------------------------------------------------------------

    @Test
    @DisplayName("a companion at a path another rule classifies is refused naming the rule; its own rule admits it")
    void aClassifiedPathIsRefusedExceptAtTheCompanionsOwnRule() throws IOException {
        Path pack = Files.createDirectories(host.resolve(PACK)).toAbsolutePath().normalize();
        KindBinding.Companion text =
                new KindBinding.Companion("../notes/x.md", Optional.empty(), ContentForm.TEXT, Optional.empty());
        KindBinding.Companion notes = new KindBinding.Companion(
                "../notes/x.md", Optional.empty(), ContentForm.MARKDOWN, Optional.of("notes"));
        KindBinding.Companion otherSchema = new KindBinding.Companion(
                "../notes/x.md", Optional.empty(), ContentForm.MARKDOWN, Optional.of("guide"));
        KindBinding.Companion readme = new KindBinding.Companion("../assets/README.md", Optional.empty());

        assertThat(Companions.resolve(RULES, pack, SKILL_FILE, text))
                .isEqualTo(new Companions.Refused(
                        "is a pack file of kind notes (file-kinds rule '*/notes/*.md'), not a companion"));
        assertThat(Companions.resolve(RULES, pack, SKILL_FILE, otherSchema))
                .isEqualTo(new Companions.Refused(
                        "is a pack file of kind notes (file-kinds rule '*/notes/*.md'), not a companion"));
        assertThat(Companions.resolve(RULES, pack, SKILL_FILE, notes))
                .as("the notes rule is the companion's own")
                .isEqualTo(new Companions.Inside(pack.resolve("notes/x.md"), "notes/x.md"));
        assertThat(Companions.resolve(Companions.Rules.bundled(), pack, SKILL_FILE, readme))
                .isEqualTo(new Companions.Refused(
                        "is excluded by file-kinds rule '*/assets/**/README.md', not a companion"));
        assertThat(Companions.resolve(Companions.Rules.bundled(), pack, SKILL_FILE, text))
                .as("positive control: the bundled table has no notes rule, so the text companion is admitted there")
                .isInstanceOf(Companions.Inside.class);
    }

    @Test
    @DisplayName("an upsert naming a classified path is a client error naming the rule, and writes nothing")
    void anUpsertAtAClassifiedPathIsAClientError() throws IOException {
        Path pack = Files.createDirectories(host.resolve(PACK));
        write(pack, "pack.yaml", PACK_YAML);
        FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy());
        SkillDso readme = dso("land", "assets/README.md", "body\n");

        assertThatThrownBy(() -> store.write(PACK_ID, SkillBinding.INSTANCE, readme))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .hasMessage("skill 'land': companion assets/README.md is excluded by file-kinds rule"
                        + " '*/assets/**/README.md', not a companion");
        assertThat(pack.resolve("assets/README.md")).doesNotExist();
        assertThat(pack.resolve("land.skill.yaml")).doesNotExist();
    }

    // ---- T024 + IC-01/E1: the fixture pack through WholePackStore and the gate comparison --------------------------

    @Test
    @DisplayName("a fixture pack with a Markdown companion round-trips through WholePackStore and the gate, green")
    void aMarkdownCompanionRoundTripsThroughTheWholePackStoreAndTheGate() throws IOException {
        Path source = pack(BODY.replace("## Detail", "\n\n## Detail"));

        Map<String, Optional<String>> observed =
                gate(source, host.resolve("out").resolve(PACK), loaded -> loaded);

        assertThat(observed)
                .containsOnlyKeys("pack.yaml", SKILL_FILE, BODY_FILE)
                .allSatisfy((path, red) -> assertThat(red).as(path).isEmpty());
        assertThat(host.resolve("out").resolve(PACK).resolve(BODY_FILE))
                .as("written in the canonical form")
                .hasContent(BODY);
    }

    @Test
    @DisplayName("positive control: one changed character inside a section is a gate difference at that section")
    void oneChangedCharacterInASectionIsAGateDifferenceAtThatSection() throws IOException {
        Path source = pack(BODY);

        Map<String, Optional<String>> observed = gate(
                source,
                host.resolve("out").resolve(PACK),
                loaded -> withBody(loaded, BODY.replace("linear.", "linear!")));

        assertThat(observed.get(BODY_FILE))
                .hasValue("sections[1].content: source Keep the history linear., written Keep the history linear!");
        assertThat(observed.get(SKILL_FILE))
                .as("the skill's own file is unchanged")
                .isEmpty();
    }

    /**
     * Loads {@code source} through a {@link WholePackStore} carrying the test binding, writes the (changed) pack to
     * {@code target}, and runs the gate's comparison ({@link WholePackRoundTripTest#observe}) with each companion in its
     * declared form. Every written file must pair with a source file.
     */
    private Map<String, Optional<String>> gate(
            Path source, Path target, UnaryOperator<WholePackStore.LoadedPack> change) {
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy(), BINDINGS, RULES);
        SortedMap<String, String> census = WholePackCensus.of(source);
        WholePackRoundTrip throughTheStore = new WholePackRoundTrip() {
            @Override
            public Set<String> modelledKinds() {
                return Set.of(DomainRoundTrip.DESCRIPTOR, "skill");
            }

            @Override
            public Map<String, Outcome> roundTrip(Path from, Path to, Map<String, String> kindOf) {
                store.write(change.apply(store.load(from)), to);
                assertThat(store.load(to).pack().violations()).as("read back").isEmpty();
                return kindOf.keySet().stream()
                        .collect(Collectors.toMap(path -> path, path -> (Outcome) new Written(path)));
            }
        };
        Set<String> paired = new TreeSet<>();

        Map<String, Optional<String>> observed = WholePackRoundTripTest.observe(
                throughTheStore,
                source,
                target,
                census,
                new TreeMap<>(),
                paired,
                WholePackCensus.formsOf(source, WholePackCensus.companionsOf(source, BINDINGS, RULES)));

        assertThat(census).containsEntry(BODY_FILE, "skill");
        assertThat(WholePackRoundTripTest.unpaired(target, paired)).isEmpty();
        return observed;
    }

    // ---- T024: the gate pairs companions with their owner's written companion ---------------------------------------

    @Test
    @DisplayName(
            "the gate pairs a text companion with the written skill's, compares it exactly and finds no extra file")
    void theGatePairsATextCompanion() throws IOException {
        Path source = pack(BODY);

        Gate faithful = domainGate(source, "faithful", pack -> pack);

        assertThat(faithful.outcomes()).containsEntry(BODY_FILE, new Written("land.md"));
        assertThat(faithful.observed())
                .allSatisfy((path, red) -> assertThat(red).as(path).isEmpty());
        assertThat(faithful.unpaired()).isEmpty();
    }

    @Test
    @DisplayName(
            "positive controls: a changed body is red, and a moved body is unpaired at the source and extra written")
    void theGateSeesAChangedOrUnpairedCompanion() throws IOException {
        Path source = pack(BODY);

        Gate changed = domainGate(
                source,
                "changed",
                pack -> withSkill(pack, skill -> dso("land", "land.md", BODY.replace("Rebase", "Rebate"))));
        Gate moved = domainGate(source, "moved", pack -> withSkill(pack, skill -> dso("land", "moved.md", BODY)));

        int offset = BODY.indexOf("Rebase") + 4;
        assertThat(changed.observed().get(BODY_FILE)).hasValue("text[" + offset + "]: source 's', written 't'");
        assertThat(moved.observed().get(BODY_FILE))
                .hasValue("refused: unpaired: the written skill land.skill.yaml names no companion land.md");
        assertThat(moved.unpaired()).containsExactly("moved.md");
    }

    private record Gate(Map<String, Outcome> outcomes, Map<String, Optional<String>> observed, List<String> unpaired) {}

    /** Today's gate over the fixture pack: {@link DomainRoundTrip} with the registry's (text) skill binding. */
    private Gate domainGate(Path source, String run, UnaryOperator<Pack> beforeSave) {
        Path target = host.resolve(run).resolve(PACK);
        SortedMap<String, String> census = WholePackCensus.of(source);
        Map<String, Outcome> outcomes = new TreeMap<>();
        WholePackRoundTrip recording = new WholePackRoundTrip() {
            private final DomainRoundTrip domain = new DomainRoundTrip(new YamlSerializationStrategy(), beforeSave);

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
        assertThat(census).containsEntry(BODY_FILE, "skill");
        assertThat(WholePackCensus.formsOf(source).apply(BODY_FILE)).isEqualTo(ContentForm.TEXT);
        return new Gate(outcomes, observed, WholePackRoundTripTest.unpaired(target, paired));
    }

    // ---- fixture ----------------------------------------------------------------------------------------------------

    private Path pack(String body) throws IOException {
        Path pack = Files.createDirectories(host.resolve(PACK));
        write(pack, "pack.yaml", PACK_YAML);
        write(pack, SKILL_FILE, skill("land", "land.md"));
        write(pack, BODY_FILE, body);
        return pack;
    }

    private static String skill(String id, String bodyPath) {
        return """
                schema_version: "1.0"
                id: %s
                title: Land a PR
                description: Use when a pull request needs landing.
                form: prompt
                body_path: %s
                """.formatted(id, bodyPath);
    }

    private static SkillDso dso(String id, String bodyPath, String body) {
        return new SkillDso(
                "1.0",
                id,
                "Land a PR",
                "Use when a pull request needs landing.",
                List.of(),
                "prompt",
                Optional.of(bodyPath),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(body));
    }

    private static WholePackStore.LoadedPack withBody(WholePackStore.LoadedPack loaded, String body) {
        Pack pack = withSkill(loaded.pack(), skill -> {
            SkillDso dso = SkillBinding.INSTANCE.toDso(skill);
            return dso.withBody(Optional.of(body));
        });
        return new WholePackStore.LoadedPack(pack, loaded.layout(), loaded.graphs(), loaded.unmodelled());
    }

    /** The pack with its one skill replaced by the skill {@code change} maps it to. */
    private static Pack withSkill(Pack pack, Function<Skill, SkillDso> change) {
        Skill skill = (Skill) pack.artifacts(ArtifactKind.SKILL).getFirst();
        return pack.with(SkillBinding.INSTANCE.toEntity(pack.id(), change.apply(skill)));
    }

    private static void write(Path pack, String file, String text) throws IOException {
        Path path = pack.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static String withTestRule(String table) {
        String files = "\nfiles:\n";
        assertThat(table).contains(files);
        return table.replace(files, files + RULE);
    }

    private static String resource(String name) {
        try (InputStream in = MarkdownCompanionTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The skill kind with its body declared as a Markdown companion validated by the {@code notes} structure schema;
     * everything else is the registry's skill binding. The body is held as canonical Markdown text in the DSO.
     */
    enum MarkdownSkill implements KindBinding<SkillDso, Skill> {
        INSTANCE;

        @Override
        public ArtifactKind kind() {
            return SkillBinding.INSTANCE.kind();
        }

        @Override
        public Class<Skill> entityType() {
            return SkillBinding.INSTANCE.entityType();
        }

        @Override
        public Class<SkillDso> dsoType() {
            return SkillBinding.INSTANCE.dsoType();
        }

        @Override
        public String idOf(SkillDso dso) {
            return SkillBinding.INSTANCE.idOf(dso);
        }

        @Override
        public Skill toEntity(PackIdentifier pack, SkillDso dso) {
            return SkillBinding.INSTANCE.toEntity(pack, dso);
        }

        @Override
        public SkillDso toDso(Skill entity) {
            return SkillBinding.INSTANCE.toDso(entity);
        }

        @Override
        public boolean hasCompanions() {
            return true;
        }

        @Override
        public List<Companion> companions(SkillDso dso) {
            return dso.bodyPath()
                    .map(path -> List.of(new Companion(
                            path, dso.body().map(MarkdownSkill::parsed), ContentForm.MARKDOWN, Optional.of("notes"))))
                    .orElse(List.of());
        }

        @Override
        public SkillDso withCompanions(SkillDso dso, Map<String, CompanionContent> contents) {
            return dso.withBody(dso.bodyPath().map(contents::get).map(Companions::text));
        }

        private static CompanionContent parsed(String body) {
            return switch (MarkdownCodec.parse(body)) {
                case MarkdownCodec.Parsed parsed -> new CompanionContent.Markdown(parsed.document());
                case ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal refusal ->
                    throw new IllegalArgumentException("the body is not Markdown: " + refusal.message());
            };
        }
    }
}
