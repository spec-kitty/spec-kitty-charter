package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A pack root spelled with {@code ..} or {@code .} segments reads in validate mode as it reads spelled plainly, and as
 * the load reads it (#319): a mission type's output template that is there is not "leaving the pack". A linked root
 * reads as the plain one and reports its configured locator. This fixture's classification does not depend on the
 * pack directory's name, so the test does not catch a switch to the real path (#284); the code comment at the fix and
 * review guard that.
 */
@DisplayName("Validate mode reads a pack root however it is spelled, as the load does (#319)")
class PackRootSpellingTest {

    private static final String PACK = "mission-type";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);
    private static final String FILE = "missions/research/research.mission-type.yaml";
    private static final String TEMPLATE = "missions/research/templates/spec-template.md";
    private static final String MISSING_DETAIL =
            PACK + "/" + FILE + " names templates/spec-template.md (named by step 'scoping'): missing";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @TempDir
    Path host;

    /** Copies the fixture to {@code parent/mission-type} with an output template that names a file it holds. */
    private static Path packWithTemplate(Path parent) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, parent);
        String withTemplate = Files.readString(pack.resolve(FILE))
                .replace(
                        "    path_pattern: spec.md\n    blocking: true\n",
                        "    path_pattern: spec.md\n    blocking: true\n    template_path: templates/spec-template.md\n");
        assertThat(withTemplate).as("the fixture names a template").contains("template_path:");
        Files.writeString(pack.resolve(FILE), withTemplate);
        Files.createDirectories(pack.resolve(TEMPLATE).getParent());
        Files.writeString(pack.resolve(TEMPLATE), "# Spec\n");
        return pack;
    }

    private static PackContents validate(String locator) {
        return new FileSystemPackInspector()
                .inspect(new PackRoot(PACK, Optional.of(PackLevel.ORG), locator), PackInspector.Scope.FULL);
    }

    private List<PackElementViolation> loadViolations(String locator) {
        return new DomainRepositoryService(
                        () -> List.of(new PackLocation(PACK, PackLevel.ORG, Path.of(locator))), strategy)
                .load(PACK_ID)
                .violations();
    }

    private static void assertSameRead(PackContents spelled, PackContents plain) {
        assertThat(spelled.unreadableFiles()).isEmpty();
        assertThat(spelled.files()).isNotEmpty().isEqualTo(plain.files());
        assertThat(spelled.artifacts()).isNotEmpty().isEqualTo(plain.artifacts());
        assertThat(spelled.stepContracts()).isEqualTo(plain.stepContracts());
    }

    private static void assertOnlyTheTemplateIsMissing(PackContents validated) {
        assertThat(validated.unreadableFiles()).singleElement().satisfies(bad -> {
            assertThat(bad.file()).isEqualTo(FILE);
            assertThat(PackPolicy.unreadable(PACK, bad))
                    .extracting(PackFinding::detail)
                    .singleElement()
                    .asString()
                    .endsWith(": missing")
                    .doesNotContain("leaves the pack")
                    .isEqualTo(MISSING_DETAIL);
        });
    }

    private static void assertLoadMissesOnlyTheTemplate(List<PackElementViolation> violations) {
        assertThat(violations)
                .containsExactly(new PackElementViolation(
                        FILE, "reference", "templates/spec-template.md (named by step 'scoping'): missing"));
    }

    @ParameterizedTest(name = "[{index}] host/{0}")
    @ValueSource(
            strings = {
                "mission-type",
                "x/../mission-type",
                "mission-type/.",
                "mission-type/missions/..",
            })
    @DisplayName(
            "a root spelled with '.' or '..' reads the template that is there, and misses only the one that is not")
    void aSpelledRootReadsAsThePlainOne(String spelling) throws IOException {
        Path pack = packWithTemplate(host);
        Files.createDirectories(host.resolve("x"));
        // Built as a string so that Path does not normalize the spelling away.
        String locator = host.toString() + "/" + spelling;
        PackContents plain = validate(pack.toString());

        assertSameRead(validate(locator), plain);
        assertThat(loadViolations(locator)).as("the load at the same spelling").isEmpty();

        Files.delete(pack.resolve(TEMPLATE));

        assertOnlyTheTemplateIsMissing(validate(locator));
        assertLoadMissesOnlyTheTemplate(loadViolations(locator));
    }

    @Test
    @DisplayName("a linked root reads as the plain one and reports its configured locator")
    void aLinkedRootReadsAsThePlainOne() throws IOException {
        SymbolicLinks.requireSupport(host);
        Path checkout = Files.createDirectories(host.resolve("checkout"));
        Path target = Files.move(packWithTemplate(checkout), checkout.resolve("other-name"));
        Path plainCopy = packWithTemplate(Files.createDirectories(host.resolve("plain")));
        Path link = SymbolicLinks.create(
                Files.createDirectories(host.resolve("packs")).resolve(PACK), target);
        String locator = link.toString();

        PackContents linked = validate(locator);
        assertThat(linked.root().locator())
                .as("the configured spelling is reported")
                .isEqualTo(locator);
        assertSameRead(linked, validate(plainCopy.toString()));
        assertThat(loadViolations(locator)).as("the load through the link").isEmpty();

        Files.delete(target.resolve(TEMPLATE));

        assertOnlyTheTemplateIsMissing(validate(locator));
        assertLoadMissesOnlyTheTemplate(loadViolations(locator));
    }
}
