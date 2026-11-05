package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import ai.speckitty.charter.kernel.domain.PackPaths;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChecksTest {

    private static final String HAS_A_VALUE = "a styleguide setting has a value";
    private static final String IS_YAML_NULL = "a cleared styleguide setting is a YAML null";
    private static final String NAMES_A_CHARTER = "a pack names its active charter";

    private final Constraint<ConfigValue.Scalar> hasAValue =
            Constraints.of(HAS_A_VALUE, Checks.haveNonNullField(ConfigValue.Scalar::raw));
    private final Constraint<ConfigValue.Scalar> isYamlNull =
            Constraints.of(IS_YAML_NULL, Checks.haveNullField(ConfigValue.Scalar::raw));
    private final Constraint<PackPaths> namesACharter = Constraints.of(
            NAMES_A_CHARTER, Checks.haveNonBlank(paths -> paths.activeCharter().orElse(null)));

    private final ConfigValue.Scalar yamlNull = new ConfigValue.Scalar(null);
    private final ConfigValue.Scalar maxLineLength = new ConfigValue.Scalar(120);

    @Test
    void haveNonNullFieldRefusesANullFieldAndAcceptsAPresentOne() {
        assertThat(hasAValue.applyTo(yamlNull)).isEqualTo(Reason.failed(HAS_A_VALUE));
        // control: the same constraint accepts a present value, even an empty string, which is not null
        assertThat(hasAValue.applyTo(maxLineLength)).isEqualTo(Reason.passed(HAS_A_VALUE));
        assertThat(hasAValue.applyTo(new ConfigValue.Scalar(""))).isEqualTo(Reason.passed(HAS_A_VALUE));
    }

    @Test
    void haveNullFieldAcceptsANullFieldAndRefusesAPresentOne() {
        assertThat(isYamlNull.applyTo(maxLineLength)).isEqualTo(Reason.failed(IS_YAML_NULL));
        // control: the same constraint accepts the YAML null
        assertThat(isYamlNull.applyTo(yamlNull)).isEqualTo(Reason.passed(IS_YAML_NULL));
    }

    @Test
    void haveNonBlankRefusesAnAbsentOrBlankTextAndAcceptsText() {
        assertThat(namesACharter.applyTo(PackPaths.none())).isEqualTo(Reason.failed(NAMES_A_CHARTER));
        assertThat(namesACharter.applyTo(new PackPaths(Optional.of(" \t"), Optional.empty())))
                .isEqualTo(Reason.failed(NAMES_A_CHARTER));
        // control: the same constraint accepts a named charter
        assertThat(namesACharter.applyTo(new PackPaths(Optional.of("charter/active.md"), Optional.empty())))
                .isEqualTo(Reason.passed(NAMES_A_CHARTER));
    }
}
