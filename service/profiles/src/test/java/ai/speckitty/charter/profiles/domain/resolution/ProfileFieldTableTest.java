package ai.speckitty.charter.profiles.domain.resolution;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
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
}
