package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code org-charter.yaml} and the descriptor's {@code active_charter} are retired (#431, FR-009 to FR-011). One
 * canonical example pack plus a root {@code org-charter.yaml}: classification, validate mode, the store's load, the
 * registration's staged load and the digest all agree that the file is retired and refuse it by name, never as
 * {@code unmodelled}. A {@code pack.yaml} that declares {@code active_charter} is refused as an unknown key of the
 * closed descriptor, not through a rule of its own.
 */
@DisplayName("org-charter.yaml and active_charter are retired (#431)")
class OrgCharterRetirementTest {

    private static final String FILE = "org-charter.yaml";
    private static final String REASON =
            "org enforcement is an activation preset with must-have items plus policy.yaml levels (#431)";

    private Path pack;

    @BeforeEach
    void anExamplePackWithAnOrgCharter(@TempDir Path host) throws IOException {
        pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        Files.writeString(pack.resolve(FILE), "schema_version: 2\nrequired_directives: []\n", StandardCharsets.UTF_8);
    }

    private String name() {
        return String.valueOf(pack.getFileName());
    }

    @Nested
    @DisplayName("the file")
    class TheFile {

        @Test
        @DisplayName("validate mode: an artifact_schema_error saying the file is retired and why")
        void validateModeRefusesIt() {
            PackContents contents = new FileSystemPackInspector()
                    .inspect(new PackRoot(name(), Optional.of(PackLevel.ORG), pack.toString()), Scope.FULL);

            assertThat(contents.unreadableFiles())
                    .extracting(FileRead.Unreadable::file)
                    .containsExactly(FILE);
            assertThat(contents.unreadableFiles().stream().flatMap(bad -> PackPolicy.unreadable(name(), bad).stream()))
                    .singleElement()
                    .satisfies(finding -> {
                        assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
                        assertThat(finding.file()).isEqualTo(FILE);
                        assertThat(finding.detail()).isEqualTo(name() + "/" + FILE + " is retired: " + REASON);
                    });
        }

        @Test
        @DisplayName("the store's load: a retired violation with the reason")
        void theLoadRefusesIt() {
            WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(pack);

            assertThat(loaded.pack().violations())
                    .extracting(violation -> violation.file() + " " + violation.kind() + " " + violation.reason())
                    .containsExactly(FILE + " retired " + REASON);
        }

        @Test
        @DisplayName("the digest takes the file in its retired tail, after the held files, and the census names it")
        void theDigestAndTheCensusCoverIt() {
            assertThat(PackFileSet.listing(pack).retired())
                    .containsOnlyKeys(FILE)
                    .containsEntry(FILE, REASON);
            assertThat(PackFileSet.digested(pack)).last().isEqualTo(FILE);
            assertThat(WholePackCensus.of(pack)).containsEntry(FILE, WholePackCensus.RETIRED + "*/" + FILE);
        }
    }

    @Nested
    @DisplayName("the descriptor key")
    class TheDescriptorKey {

        /** The store's wording of the closed descriptor's refusal: the schema violation, naming the undeclared key. */
        private static final String UNKNOWN_KEY = "pack.yaml of org-base cannot be read: additionalProperties violated"
                + " at the document: undeclared key 'active_charter'";

        @BeforeEach
        void theDescriptorDeclaresActiveCharter() throws IOException {
            Files.delete(pack.resolve(FILE));
            Path descriptor = pack.resolve("pack.yaml");
            Files.writeString(
                    descriptor,
                    Files.readString(descriptor, StandardCharsets.UTF_8) + "active_charter: charter.yaml\n",
                    StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("control: the same pack without the key loads and registers")
        void theSamePackWithoutTheKeyIsFine() throws IOException {
            Path descriptor = pack.resolve("pack.yaml");
            Files.writeString(
                    descriptor,
                    Files.readString(descriptor, StandardCharsets.UTF_8).replace("active_charter: charter.yaml\n", ""),
                    StandardCharsets.UTF_8);
            WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

            assertThat(store.load(pack).pack().violations()).isEmpty();
            assertThat(store.loadStaged(new StagedPack(pack))
                            .pack()
                            .descriptor()
                            .name())
                    .isEqualTo("org-base");
        }

        @Test
        @DisplayName(
                "validate mode: a pack_unreadable finding naming the key as one the closed descriptor does not declare")
        void validateModeNamesTheKey() {
            PackRoot root = new PackRoot(name(), Optional.of(PackLevel.ORG), pack.toString());

            List<PackFinding> findings = new PackValidation(new FileSystemPackInspector())
                    .validate(root)
                    .findings();

            assertThat(findings)
                    .filteredOn(finding -> finding.code() == PackFindingCode.PACK_UNREADABLE)
                    .singleElement()
                    .satisfies(finding -> {
                        assertThat(finding.file()).isEqualTo("pack.yaml");
                        assertThat(finding.detail()).isEqualTo("pack.yaml of org-base: unknown key 'active_charter'");
                    });
        }

        @Test
        @DisplayName("the store's load: the pack is unreadable, 500 pack_unreadable naming the key")
        void theLoadRefusesIt() {
            WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

            assertThatThrownBy(() -> store.load(pack)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                assertThat(refusal.getMessage()).isEqualTo(UNKNOWN_KEY);
            });
        }

        @Test
        @DisplayName(
                "registration: 422 validation_failed holding one pack_unreadable violation for the descriptor, naming"
                        + " the key")
        void registrationRefusesIt() {
            WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

            assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                        assertThat(refusal.violations())
                                .containsExactly(new RefusalViolation("pack", "pack_unreadable", UNKNOWN_KEY));
                    });
        }
    }
}
