package ai.speckitty.charter.missions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** A skeleton is named by its template's id without the type's prefix; a template outside the type's directory is a bug. */
class MissionTypeHoldingTest {

    private static final String READ = "The read of the mission type software-dev in pack main";
    private static final StoredFile CONTENT = StoredFile.of("# X\n".getBytes(StandardCharsets.UTF_8));

    @Test
    void aSkeletonIsItsTemplatesIdWithoutTheTypesPrefix() {
        assertThat(MissionTypeHolding.StoredSkeleton.ofTemplate(
                        "software-dev/divio/x.md", "software-dev", CONTENT, READ))
                .isEqualTo(new MissionTypeHolding.StoredSkeleton("divio/x.md", CONTENT));
    }

    @Test
    void aTemplateOutsideTheTypesDirectoryIsABug() {
        assertThatThrownBy(
                        () -> MissionTypeHolding.StoredSkeleton.ofTemplate("other/x.md", "software-dev", CONTENT, READ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(READ + " holds the template other/x.md outside the type's templates/ directory.");
        assertThatThrownBy(() -> MissionTypeHolding.StoredSkeleton.ofTemplate(
                        "software-devx/x.md", "software-dev", CONTENT, READ))
                .as("the prefix is the type's id and a slash")
                .isInstanceOf(IllegalStateException.class);
    }
}
