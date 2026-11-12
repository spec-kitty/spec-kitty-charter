package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Skill;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every component that decides what a pack file is agrees about the same pack (squad A9, #451). The pack holds two
 * Markdown files that are held with their artifact, a skill's body and a toolguide's guide, and three files that are not
 * pack content: an author's note the {@code ignored} fallback takes, an orientation README an exclusion names, and an
 * excluded YAML file. Classification, validate mode, the load, the digest, the gate census and the staged load must all
 * hold the first two and exclude the other three: the fallback never captures a file a companion claim takes.
 */
@DisplayName("Classification, validate mode, load, digest, census and staged load agree on excluded files (#451)")
class ExcludedFilesAgreementTest {

    private static final String SKILL_FILE = "skills/land-pr.skill.yaml";
    private static final String SKILL_BODY = "skills/land-pr.skill.md";
    private static final String GUIDE = "toolguides/git-agent-commit-signing.guide.md";
    private static final String NOTE = "docs/notes.md";
    private static final String README = "missions/x/templates/README.md";
    private static final String DECLARATION = "missions/x/templates/plan-field-declaration.yaml";

    private static final List<String> EXCLUDED = List.of(NOTE, README, DECLARATION);

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    private Path pack;

    @BeforeEach
    void setUp(@TempDir Path host) throws IOException {
        pack = PackFixtures.copyOf("toolguide", host);
        write(SKILL_FILE, """
                schema_version: "1.0"
                id: land-pr
                title: Land a PR
                description: Use when a pull request needs landing.
                form: prompt
                body_path: land-pr.skill.md
                invocation:
                  user_invocable: true
                  side_effects: [git-push]
                """);
        write(SKILL_BODY, "Goal: land it.\n");
        write(NOTE, "# Notes\n\nAn author's note.\n");
        write(README, "# Templates of x\n");
        write(DECLARATION, "anything: goes\n");
    }

    private void write(String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private String name() {
        return String.valueOf(pack.getFileName());
    }

    @Test
    @DisplayName("classification: the fallback takes the note; a companion claim still wins over it for the skill body")
    void classificationAndTheCompanionClaimAgree() {
        PackFileKind table = PackFileKind.bundled();

        for (String excluded : EXCLUDED) {
            assertThat(table.classify(name() + "/" + excluded)).as(excluded).isInstanceOf(PackFileKind.Excluded.class);
        }
        assertThat(table.classify(name() + "/" + GUIDE))
                .as("a toolguide's guide is a files rule's, not the fallback's")
                .isInstanceOf(PackFileKind.Mapped.class);
        assertThat(table.classify(name() + "/" + SKILL_BODY))
                .as("on its own the body is Markdown nothing models")
                .isInstanceOf(PackFileKind.Excluded.class);
        assertThat(Companions.resolve(
                        Companions.Rules.bundled(),
                        pack.toAbsolutePath().normalize(),
                        SKILL_FILE,
                        new KindBinding.Companion(
                                "land-pr.skill.md", Optional.empty(), ContentForm.TEXT, Optional.empty())))
                .as("but its skill claims it, and the claim check does not see the fallback")
                .isInstanceOf(Companions.Inside.class);
    }

    @Test
    @DisplayName("validate mode lists the three excluded files and never the companions, and reads none of them")
    void validateModeAgrees() {
        PackContents contents = new FileSystemPackInspector()
                .inspect(new PackRoot(name(), Optional.of(PackLevel.ORG), pack.toString()), Scope.FULL);

        assertThat(contents.files().stream()
                        .filter(file -> file.exclusionClass().isPresent())
                        .map(PackContents.ClassifiedFile::file))
                .containsExactlyInAnyOrderElementsOf(EXCLUDED);
        assertThat(contents.files().stream().map(PackContents.ClassifiedFile::file))
                .doesNotContain(SKILL_BODY);
        assertThat(contents.unreadableFiles()).isEmpty();
    }

    @Test
    @DisplayName("the load models the companions and lists the excluded files apart, with nothing unmodelled")
    void loadAgrees() {
        WholePackStore.LoadedPack loaded = store.load(pack);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.unmodelled()).isEmpty();
        assertThat(loaded.excluded())
                .extracting(WholePackWriteStore.ExcludedFile::file)
                .containsExactlyInAnyOrderElementsOf(EXCLUDED);
        assertThat(loaded.pack().artifacts(ArtifactKind.SKILL, Skill.class))
                .singleElement()
                .satisfies(skill -> assertThat(skill.body()).isPresent());
        assertThat(loaded.pack().toolguides())
                .singleElement()
                .satisfies(
                        toolguide -> assertThat(toolguide.guidePath()).isEqualTo("git-agent-commit-signing.guide.md"));
    }

    @Test
    @DisplayName("the digest covers the companions and none of the excluded files")
    void digestAgrees() {
        assertThat(PackFileSet.digested(pack))
                .contains(SKILL_FILE, SKILL_BODY, GUIDE)
                .doesNotContainAnyElementsOf(EXCLUDED);
    }

    @Test
    @DisplayName("the gate census takes the companions with their owners and gives the excluded files their own kind")
    void censusAgrees() {
        SortedMap<String, String> census = WholePackCensus.of(pack);

        assertThat(census).containsEntry(SKILL_BODY, "skill").containsEntry(GUIDE, "toolguide");
        for (String excluded : EXCLUDED) {
            assertThat(census.get(excluded)).as(excluded).startsWith(WholePackCensus.EXCLUDED);
        }
        assertThat(census.values()).noneMatch(kind -> kind.startsWith(WholePackCensus.OTHER));
    }

    @Test
    @DisplayName("the staged load refuses nothing, reports each excluded file, and the strip leaves only held files")
    void stagedLoadAndTheStripAgree() throws IOException {
        StagedPack staged = new StagedPack(pack);

        WholePackWriteStore.Contents contents = store.loadStaged(staged);

        assertThat(contents.warnings())
                .extracting(PackFinding::code, PackFinding::file)
                .containsExactlyInAnyOrder(
                        tuple(PackFindingCode.FILE_NOT_HELD, DECLARATION),
                        tuple(PackFindingCode.FILE_NOT_HELD, NOTE),
                        tuple(PackFindingCode.FILE_NOT_HELD, README));
        assertThat(contents.notHeld())
                .as("the strip's list is the one the warnings are derived from")
                .extracting(WholePackWriteStore.ExcludedFile::file)
                .containsExactlyInAnyOrder(DECLARATION, NOTE, README);
        store.removeNotHeld(staged, contents);
        try (Stream<Path> walk = Files.walk(pack)) {
            Set<String> left = walk.filter(Files::isRegularFile)
                    .map(path -> pack.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
            assertThat(left).contains(SKILL_FILE, SKILL_BODY, GUIDE).doesNotContainAnyElementsOf(EXCLUDED);
        }
        assertThat(PackFileSet.digested(pack))
                .as("after the strip the digest and the tree agree")
                .contains(SKILL_BODY, GUIDE);
    }
}
