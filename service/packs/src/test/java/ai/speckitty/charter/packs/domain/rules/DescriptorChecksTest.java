package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DescriptorChecksTest {

    private static PackDescriptor descriptor(String version, Optional<String> requiresCore, String... ranges) {
        return new PackDescriptor(
                "01ARWG13C00000000000001ABC",
                version,
                Optional.empty(),
                "qa",
                Optional.of(PackLevel.ORG),
                requiresCore,
                PackPaths.none(),
                java.util.Arrays.stream(ranges)
                        .map(range -> new PackDependency("01M4P01D5TWB5FKZGNNQG3943R", range))
                        .toList());
    }

    @Test
    void aWellFormedDescriptorHasNoFinding() {
        assertThat(DescriptorChecks.faults("qa", descriptor("1.0.0", Optional.of(">=3.2"), ">=1.0,<2")))
                .isEmpty();
    }

    @Test
    void anAbsentRequiresCoreIsNoFinding() {
        assertThat(DescriptorChecks.faults("qa", descriptor("1.0.0", Optional.empty())))
                .isEmpty();
    }

    @Test
    void eachMalformedValueIsOneFindingAtItsPlaceInDeclarationOrder() {
        assertThat(DescriptorChecks.faults("qa", descriptor("one", Optional.of("banana"), ">=1.0,<2", "nope", "~=1")))
                .containsExactly(
                        new DescriptorChecks.Fault(
                                DescriptorChecks.Check.REQUIRES_CORE,
                                List.of("requires_core"),
                                "pack.yaml of qa: requires_core 'banana' is not a PEP 440 version specifier"),
                        new DescriptorChecks.Fault(
                                DescriptorChecks.Check.PACK_VERSION,
                                List.of("pack_version"),
                                "pack.yaml of qa: pack_version 'one' is not a PEP 440 version"),
                        new DescriptorChecks.Fault(
                                DescriptorChecks.Check.DEPENDENCY_RANGE,
                                List.of("depends_on", "1", "range"),
                                "pack.yaml of qa: depends_on[1] range 'nope' is not a PEP 440 version specifier"),
                        new DescriptorChecks.Fault(
                                DescriptorChecks.Check.DEPENDENCY_RANGE,
                                List.of("depends_on", "2", "range"),
                                "pack.yaml of qa: depends_on[2] range '~=1' is not a PEP 440 version specifier"));
    }

    @Test
    void theTextNamesThePackItIsGiven() {
        assertThat(DescriptorChecks.faults("other", descriptor("one", Optional.empty())))
                .extracting(DescriptorChecks.Fault::detail)
                .containsExactly("pack.yaml of other: pack_version 'one' is not a PEP 440 version");
    }
}
