package ai.speckitty.charter.profiles.domain.resolution;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The folds' field table is single-sourced: every field {@link AgentProfile} holds, its pack aside, has one row, so a new
 * schema field cannot silently skip the merge or the inheritance.
 */
class ProfileFieldTableTest {

    @Test
    void theTableListsEveryFieldOfTheEntityButItsPackAndNothingElse() {
        var entityFields = Arrays.stream(AgentProfile.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .filter(name -> !name.equals("packId"))
                .collect(Collectors.toSet());

        assertThat(ProfileField.values().stream().map(ProfileField::javaField))
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(entityFields);
    }

    @Test
    void theIdentityFieldsAreTheSevenOfRulingD5() {
        assertThat(ProfileField.values().stream().filter(ProfileField::identity).map(ProfileField::yamlName))
                .containsExactlyInAnyOrder(
                        "profile-id", "name", "purpose", "role", "alt-roles", "performer", "routing-priority");
    }

    @Test
    void theSchemaNamesAreDistinct() {
        assertThat(ProfileField.values().stream().map(ProfileField::yamlName)).doesNotHaveDuplicates();
    }

    /** An exclusion of a reference list's value is shaped as what it names, as the list's own entries are (#673). */
    @Test
    void aBareAndAQualifiedExclusionOfOneArtifactAreOneShape() {
        assertThat(ProfileField.EXCLUDING.shapeOf(excluding("DIRECTIVE_001", "tdd")))
                .isEqualTo(
                        ProfileField.EXCLUDING.shapeOf(excluding("built-in::directive:DIRECTIVE_001", "built-in::tdd")))
                .isEqualTo(Map.of(
                        "directive-references",
                        List.of("built-in::directive:DIRECTIVE_001"),
                        "tactic-references",
                        List.of("built-in::tactic:tdd")));
    }

    private static AgentProfile excluding(String directive, String tactic) {
        return AgentProfile.builder()
                .packId(new PackIdentifier("built-in"))
                .id(new AgentProfileIdentifier("p"))
                .name("P")
                .role("implementer")
                .purpose("Purpose")
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                .excluding(Optional.of(new Exclusion.FieldValues(
                        Map.of("directive-references", List.of(directive), "tactic-references", List.of(tactic)))))
                .build();
    }
}
