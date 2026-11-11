package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The reader reports {@code pack.yaml} as facts; the texts below are the ones {@link PackPolicy} writes from them. */
class PackDescriptorReaderTest {

    private static final String VALID = "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\n";

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

    /** ADR-0063 decision 1: the descriptor names its policy file; without the key the pack has none. */
    @Test
    void aNamedMutabilityPolicyIsReportedAsThePathAndAnAbsentOneIsEmpty() {
        assertThat(read(VALID + "mutability_policy: policy.yaml\n").paths().mutabilityPolicy())
                .contains("policy.yaml");
        assertThat(read(VALID).paths().mutabilityPolicy()).isEmpty();
    }

    /** The key survives the write back: a descriptor written and read again still names its policy file. */
    @Test
    void aNamedMutabilityPolicyRoundTripsThroughTheDescriptorWrite() {
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackDescriptor named = read(VALID + "active_charter: org-charter.yaml\nmutability_policy: policy.yaml\n");

        String written = strategy.writeDescriptor(PackDescriptorDso.of(named));

        assertThat(written).endsWith("active_charter: org-charter.yaml\nmutability_policy: policy.yaml\n");
        assertThat(read(written)).isEqualTo(named);
    }

    /** ADR-0066: a declared {@code schema_version} is read, written back first, and read again unchanged. */
    @Test
    void aDeclaredSchemaVersionRoundTripsThroughTheDescriptorWrite() {
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackDescriptor versioned = read("schema_version: \"1.0\"\n" + VALID);
        assertThat(versioned.schemaVersion()).contains("1.0");

        String written = strategy.writeDescriptor(PackDescriptorDso.of(versioned));

        assertThat(written).startsWith("schema_version: '1.0'\n").contains("pack_id: 01ARWG13C00000000000001ABC\n");
        assertThat(read(written)).isEqualTo(versioned);
        assertThat(read(written).schemaVersion()).contains("1.0");
    }

    /** An unversioned descriptor is the legacy shape: it stays unversioned, and no key is invented on write. */
    @Test
    void anUnversionedDescriptorStaysUnversionedThroughTheDescriptorWrite() {
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackDescriptor legacy = read(VALID);
        assertThat(legacy.schemaVersion()).isEmpty();

        String written = strategy.writeDescriptor(PackDescriptorDso.of(legacy));

        assertThat(written).doesNotContain("schema_version");
        assertThat(read(written).schemaVersion()).isEmpty();
    }

    @Test
    void anAbsentDependsOnIsEmptyNotDefaulted() {
        assertThat(read(VALID).dependencies()).isEmpty();
    }

    @Test
    void declaredDependenciesAreReportedInFileOrder() {
        PackDescriptor descriptor = read(VALID + """
                pack_level: org
                depends_on:
                  - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                    range: '>=1.0,<2'
                  - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
                    range: ==1.2.3
                """);

        assertThat(descriptor.dependencies())
                .containsExactly(
                        new PackDependency("01M4P01D5TWB5FKZGNNQG3943R", ">=1.0,<2"),
                        new PackDependency("01M4P01D5TWB5FKZGNNQG3943S", "==1.2.3"));
    }

    @Test
    void aDependencyWithoutItsRangeIsUnreadable() {
        assertThat(details(VALID + "depends_on:\n  - pack_id: 01M4P01D5TWB5FKZGNNQG3943R\n"))
                .isNotEmpty();
    }

    @Test
    void anAbsentOverridesIsEmptyNotDefaulted() {
        assertThat(read(VALID).overrides()).isEmpty();
    }

    @Test
    void declaredOverridesAreReportedInFileOrder() {
        PackDescriptor descriptor = read(VALID + """
                overrides:
                  - artifact: directive:DIRECTIVE_009
                    member: 01M4P01D5TWB5FKZGNNQG3943R
                  - artifact: tactic:alpha-tactic
                    member: 01M4P01D5TWB5FKZGNNQG3943S
                """);

        assertThat(descriptor.overrides())
                .containsExactly(
                        new PackOverride("directive:DIRECTIVE_009", "01M4P01D5TWB5FKZGNNQG3943R"),
                        new PackOverride("tactic:alpha-tactic", "01M4P01D5TWB5FKZGNNQG3943S"));
    }

    @Test
    void anOverrideWithoutItsMemberIsUnreadable() {
        assertThat(details(VALID + "overrides:\n  - artifact: directive:DIRECTIVE_009\n"))
                .isNotEmpty();
    }

    @Test
    void aSecondOverrideOnOneArtifactIsUnreadableAsTheUniqueItemsKeyword() {
        String twice = VALID + """
                overrides:
                  - artifact: directive:DIRECTIVE_009
                    member: 01M4P01D5TWB5FKZGNNQG3943R
                  - artifact: directive:DIRECTIVE_009
                    member: 01M4P01D5TWB5FKZGNNQG3943S
                """;
        String qualifiedTwice = VALID + """
                overrides:
                  - artifact: directive:DIRECTIVE_009
                    member: 01M4P01D5TWB5FKZGNNQG3943R
                  - artifact: core::directive:DIRECTIVE_009
                    member: 01M4P01D5TWB5FKZGNNQG3943S
                """;

        assertThat(details(twice)).containsExactly("pack.yaml of qa: '/overrides' breaks schema keyword 'uniqueItems'");
        // the qualifier says where an artifact is read, not which it is: a qualified and a bare edge are one artifact
        assertThat(details(qualifiedTwice))
                .containsExactly("pack.yaml of qa: '/overrides' breaks schema keyword 'uniqueItems'");
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
        assertThat(descriptor.paths().activeCharter()).contains("org-charter.yaml");
        assertThat(descriptor.paths().packDrg()).contains("drg/fragment.yaml");
    }

    @Test
    void anAbsentOrNullActiveCharterAndPackDrgAreEmpty() {
        PackDescriptor absent = read(VALID);
        PackDescriptor nulls = read(VALID + "active_charter: null\npack_drg: ~\n");

        assertThat(absent.paths().activeCharter()).isEmpty();
        assertThat(absent.paths().packDrg()).isEmpty();
        assertThat(nulls.paths().activeCharter()).isEmpty();
        assertThat(nulls.paths().packDrg()).isEmpty();
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

    @ParameterizedTest
    @ValueSource(strings = {"surprise", "accompanies_doctrine_pack"})
    void aKeyTheSchemaDoesNotDeclareMakesTheDescriptorUnreadableNamingTheKey(String key) {
        read(VALID);

        assertThat(unreadable(VALID + key + ": x\n").violations())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.keyword()).isEqualTo("additionalProperties");
                    assertThat(v.pointer()).isEmpty();
                });
        assertThat(details(VALID + key + ": x\n")).containsExactly("pack.yaml of qa: unknown key '" + key + "'");
    }

    @Test
    void theDeclaredKeysAreTheSchemasNotAListInTheReader() {
        List<String> declared = PackSchemas.bundled().declaredProperties("pack-descriptor");

        assertThat(declared)
                .containsExactly(
                        "schema_version",
                        "pack_id",
                        "pack_version",
                        "parent_pack",
                        "name",
                        "pack_level",
                        "depends_on",
                        "overrides",
                        "requires_core",
                        "active_charter",
                        "pack_drg",
                        "mutability_policy",
                        "fallback_profile");
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
        FileRead.Unreadable<PackDescriptor> bad = unreadable("pack_id: 01ARWG13C00000000000001ABC\nname: qa\n");

        assertThat(bad.violations()).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("required");
            assertThat(v.property()).contains("pack_version");
        });
        assertThat(details("pack_id: 01ARWG13C00000000000001ABC\nname: qa\n"))
                .containsExactly("pack.yaml of qa has no pack_version");
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
        assertThat(details("pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: " + form + "\n"))
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
    void anUndeclaredKeyIsRefusedWhateverItsValue(String extra) {
        assertThat(details(VALID + extra)).containsExactly("pack.yaml of qa: unknown key 'extra'");
    }

    @Test
    void aNonFiniteNumberWhereAStringIsDeclaredIsStillRefused() {
        assertThat(unreadable(VALID.replace("pack_version: 1.0.0", "pack_version: .nan"))
                        .violations())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.pointer()).containsExactly("pack_version");
                    assertThat(v.keyword()).isEqualTo("type");
                    assertThat(v.value()).contains("NaN");
                });
    }

    @Test
    void aNonFiniteNumberWhereAStringIsDeclaredHasTheTextOfAnyOtherNumber() {
        for (String number : List.of("1.5", ".nan", ".inf", "-.inf", "1e400")) {
            assertThat(PackPolicy.unreadable(
                            "qa", unreadable(VALID.replace("pack_version: 1.0.0", "pack_version: " + number))))
                    .extracting(PackFinding::detail)
                    .as(number)
                    .containsExactly("pack.yaml of qa: pack_version must be a string");
        }
    }

    /** Each identity field that follows a grammar, with one value that breaks it (FR-003, C-003). */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "pack_id: 01ABC|pack_id",
                "pack_id: 01arwg13c00000000000001abc|pack_id",
                "pack_id: 01ARWG13C0000000000000OLIU|pack_id",
                "parent_pack: 01ABC|parent_pack",
                "name: beta pack|name",
                "name: Beta|name",
                "name: _beta|name",
                "name: qa::x|name"
            })
    void anIdentityThatBreaksItsGrammarMakesThePackUnreadable(String line, String field) {
        String key = line.substring(0, line.indexOf(':') + 1);
        String text = VALID.lines().filter(kept -> !kept.startsWith(key)).collect(Collectors.joining("\n", "", "\n"))
                + line + "\n";

        assertThat(unreadable(text).violations())
                .anySatisfy(violation -> assertThat(violation.pointer()).startsWith(field));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "depends_on:\n  - {pack_id: 01ABC, range: '>=1'}\npack_level: org\n",
                "overrides:\n  - {artifact: 'directive:X', member: 01ABC}\n"
            })
    void aNestedPackIdThatIsNoUlidMakesThePackUnreadable(String declaration) {
        assertThat(unreadable(VALID + declaration).violations()).isNotEmpty();
    }

    @Test
    void anEmptyPackVersionIsRefusedWithItsOwnDetail() {
        assertThat(details("pack_id: 01ARWG13C00000000000001ABC\npack_version: ''\nname: qa\n"))
                .containsExactly("pack.yaml of qa: pack_version must not be empty");
    }

    /** The schema's {@code allOf/if}: a descriptor that depends on a pack declares its own level; one with none need not. */
    @Test
    void aDescriptorThatDependsOnAPackMustDeclareItsLevel() {
        String edge = "depends_on:\n  - {pack_id: 01M4P01D5TWB5FKZGNNQG3943R, range: '>=1'}\n";

        assertThat(unreadable(VALID + edge).violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.keyword()).isEqualTo("required"))
                .satisfies(violation -> assertThat(violation.args()).containsValue("pack_level"));
        assertThat(read(VALID + edge + "pack_level: org\n").dependencies()).hasSize(1);
        assertThat(read(VALID + "depends_on: []\n").dependencies()).isEmpty();
    }
}
