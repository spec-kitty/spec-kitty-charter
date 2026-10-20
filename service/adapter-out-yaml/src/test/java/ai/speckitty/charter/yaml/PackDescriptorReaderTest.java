package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackFinding;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.PackPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The reader reports {@code pack.yaml} as facts; the texts below are the ones {@link PackPolicy} writes from them. */
class PackDescriptorReaderTest {

    private static final String VALID = "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\n";

    private static PackDescriptor read(String text) {
        FileRead<PackDescriptor> read = PackDescriptorReader.read(text);
        assertThat(read).isInstanceOf(FileRead.Read.class);
        assertThat(read.file()).isEqualTo("pack.yaml");
        return ((FileRead.Read<PackDescriptor>) read).value();
    }

    private static FileRead.Unreadable<PackDescriptor> unreadable(String text) {
        FileRead<PackDescriptor> read = PackDescriptorReader.read(text);
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        assertThat(read.file()).isEqualTo("pack.yaml");
        return (FileRead.Unreadable<PackDescriptor>) read;
    }

    private static List<String> details(String text) {
        return PackPolicy.unreadable("qa", unreadable(text)).stream()
                .map(PackFinding::detail)
                .toList();
    }

    // ---- a descriptor that conforms ---------------------------------------------------------------

    @Test
    void aDeclaredLevelIsReportedAsAFact() {
        assertThat(read(VALID + "pack_level: built-in\n").declaredLevel()).contains(PackLevel.BUILT_IN);
    }

    @Test
    void anAbsentLevelIsReportedAsEmptyNotDefaulted() {
        assertThat(read(VALID).declaredLevel()).isEqualTo(Optional.empty());
    }

    @Test
    void everyFieldIsReportedAsWritten() {
        PackDescriptor descriptor = read("""
                pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                pack_version: 1.0.0
                parent_pack: 01M4P01D5TWB5FKZGNNQG3943S
                name: spec-kitty-internal
                pack_level: org
                requires_core: '>=4.0,<5'
                active_charter: org-charter.yaml
                pack_drg: drg/fragment.yaml
                """);

        assertThat(descriptor.packId()).isEqualTo("01M4P01D5TWB5FKZGNNQG3943R");
        assertThat(descriptor.packVersion()).isEqualTo("1.0.0");
        assertThat(descriptor.parentPack()).contains("01M4P01D5TWB5FKZGNNQG3943S");
        assertThat(descriptor.name()).isEqualTo("spec-kitty-internal");
        assertThat(descriptor.declaredLevel()).contains(PackLevel.ORG);
        assertThat(descriptor.requiresCore()).contains(">=4.0,<5");
        assertThat(descriptor.undeclaredKeys()).isEmpty();
    }

    @Test
    void aNullParentAndAnAbsentRequiresCoreAreEmpty() {
        PackDescriptor descriptor = read(VALID + "parent_pack: null\nrequires_core: null\n");

        assertThat(descriptor.parentPack()).isEmpty();
        assertThat(descriptor.requiresCore()).isEmpty();
        assertThat(read(VALID).requiresCore()).isEmpty();
    }

    @Test
    void aRequiresCoreThatIsNoSpecifierIsStillJustAFactTheReaderDoesNotJudge() {
        assertThat(read(VALID + "requires_core: banana\n").requiresCore()).contains("banana");
    }

    @Test
    void everyKeyTheSchemaDoesNotDeclareIsUndeclaredAndSortedRetiredFieldsIncluded() {
        PackDescriptor descriptor = read(VALID + "zeta: 1\naccompanies_doctrine_pack: x\nmiddle: [a]\n");

        assertThat(descriptor.undeclaredKeys()).containsExactly("accompanies_doctrine_pack", "middle", "zeta");
    }

    @Test
    void theUndeclaredKeysComeFromTheSchemaNotFromAListInTheReader() {
        List<String> declared = PackSchemas.bundled().declaredProperties("pack-descriptor");

        assertThat(declared)
                .containsExactly(
                        "pack_id",
                        "pack_version",
                        "parent_pack",
                        "name",
                        "pack_level",
                        "requires_core",
                        "active_charter",
                        "pack_drg");
    }

    // ---- a descriptor that is not YAML or breaks its schema ------------------------------------------

    @Test
    void aDescriptorThatIsNotYamlIsAParseFailureAndItsTextHasNoParserLine() {
        FileRead.Unreadable<PackDescriptor> bad = unreadable("pack_id: [\n");

        assertThat(bad.parse()).isNotNull();
        assertThat(bad.violations()).isEmpty();
        assertThat(PackPolicy.unreadable("qa", bad))
                .extracting(PackFinding::detail)
                .containsExactly("pack.yaml of qa is not valid YAML");
    }

    @Test
    void aDocumentThatIsNotAMappingIsTheKeywordTypeAtTheRoot() {
        FileRead.Unreadable<PackDescriptor> bad = unreadable("- a\n- b\n");

        assertThat(bad.violations()).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("type");
            assertThat(v.pointer()).isEmpty();
        });
        assertThat(details("- a\n- b\n")).containsExactly("pack.yaml of qa is not a mapping");
    }

    @Test
    void aMissingRequiredKeyIsTheKeywordRequiredNamingTheKey() {
        FileRead.Unreadable<PackDescriptor> bad = unreadable("pack_id: 01ABC\nname: qa\n");

        assertThat(bad.violations()).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("required");
            assertThat(v.property()).contains("pack_version");
        });
        assertThat(details("pack_id: 01ABC\nname: qa\n")).containsExactly("pack.yaml of qa has no pack_version");
    }

    @Test
    void aNonStringValueIsTheKeywordTypeAtTheKey() {
        FileRead.Unreadable<PackDescriptor> bad = unreadable("pack_id: [a]\npack_version: 1.0.0\nname: qa\n");

        SchemaViolation violation = bad.violations().get(0);
        assertThat(violation.keyword()).isEqualTo("type");
        assertThat(violation.pointer()).containsExactly("pack_id");
        assertThat(details("pack_id: [a]\npack_version: 1.0.0\nname: qa\n"))
                .containsExactly("pack.yaml of qa: pack_id must be a string");
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "Null", "NULL"})
    void aPlainNullFormInARequiredStringIsRefusedLikeAnExplicitNull(String form) {
        assertThat(details("pack_id: 01ABC\npack_version: 1.0.0\nname: " + form + "\n"))
                .containsExactly("pack.yaml of qa: name must be a string");
    }

    @Test
    void anUnknownPackLevelIsTheKeywordEnumNamingTheToken() {
        assertThat(details(VALID + "pack_level: galactic\n"))
                .containsExactly("pack.yaml of qa declares an unknown pack_level 'galactic'");
    }

    @Test
    void theRequiredKeysAreReportedBeforeThePackLevelAndTheRequiresCore() {
        assertThat(details("name: qa\npack_level: galactic\nrequires_core: [x]\n"))
                .containsExactly(
                        "pack.yaml of qa has no pack_id",
                        "pack.yaml of qa has no pack_version",
                        "pack.yaml of qa declares an unknown pack_level 'galactic'",
                        "pack.yaml of qa: requires_core must be a string");
    }

    @ParameterizedTest
    @ValueSource(strings = {"extra: .nan\n", "extra: {a: .inf}\n", "extra: [null, .nan]\n", "extra: [-.inf, 1e400]\n"})
    void aNonFiniteNumberInAnUndeclaredKeyIsAcceptedLikeAnyOtherValue(String extra) {
        assertThat(read(VALID + extra).undeclaredKeys()).containsExactly("extra");
    }

    @Test
    void aNonFiniteNumberWhereAStringIsDeclaredIsStillRefused() {
        assertThat(unreadable(VALID.replace("name: qa", "name: .nan")).violations())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.pointer()).containsExactly("name");
                    assertThat(v.keyword()).isEqualTo("type");
                    assertThat(v.value()).contains("NaN");
                });
    }

    @Test
    void aNonFiniteNumberWhereAStringIsDeclaredHasTheTextOfAnyOtherNumber() {
        for (String number : List.of("1.5", ".nan", ".inf", "-.inf", "1e400")) {
            assertThat(PackPolicy.unreadable("qa", unreadable(VALID.replace("name: qa", "name: " + number))))
                    .extracting(PackFinding::detail)
                    .as(number)
                    .containsExactly("pack.yaml of qa: name must be a string");
        }
    }

    @Test
    void anEmptyPackVersionIsRefusedWithItsOwnDetail() {
        assertThat(details("pack_id: 01ABC\npack_version: ''\nname: qa\n"))
                .containsExactly("pack.yaml of qa: pack_version must not be empty");
    }
}
