package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pins the texts {@link PackDescriptorReader} refuses with, and the declared level it reports as a fact. */
class PackDescriptorReaderTest {

    private static final String VALID = "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\n";

    private static PackDescriptor read(String text) {
        return PackDescriptorReader.read(text, "qa");
    }

    private static void assertRefusedWith(String text, String detail) {
        assertThatThrownBy(() -> read(text))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage(detail);
    }

    @Test
    void anUnknownPackLevelIsRefusedNamingTheToken() {
        assertRefusedWith(
                VALID + "pack_level: galactic\n", "pack.yaml of qa declares an unknown pack_level 'galactic'");
    }

    @Test
    void aMissingRequiredKeyIsRefusedNamingTheKey() {
        assertRefusedWith("pack_id: 01ABC\nname: qa\n", "pack.yaml of qa has no pack_version");
    }

    @Test
    void aNonStringValueIsRefusedNamingTheKey() {
        assertRefusedWith("pack_id: [a]\npack_version: 1.0.0\nname: qa\n", "pack.yaml of qa: pack_id must be a string");
    }

    @Test
    void aDocumentThatIsNotAMappingIsRefused() {
        assertRefusedWith("- a\n- b\n", "pack.yaml of qa is not a mapping");
    }

    @Test
    void aDeclaredLevelIsReportedAsAFact() {
        assertThat(read(VALID + "pack_level: built-in\n").declaredLevel()).contains(PackLevel.BUILT_IN);
    }

    @Test
    void anAbsentLevelIsReportedAsEmptyNotDefaulted() {
        assertThat(read(VALID).declaredLevel()).isEqualTo(Optional.empty());
    }
}
