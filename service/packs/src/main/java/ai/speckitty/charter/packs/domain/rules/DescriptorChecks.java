package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.Pep440;
import ai.speckitty.charter.kernel.domain.Pep440Range;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The descriptor's well-formedness: a pure format function over the values {@code pack.yaml} states. Validate mode,
 * the offering and the resolver's precondition all call it, so one malformed range gets one answer, whichever path
 * reads the pack.
 */
public final class DescriptorChecks {

    private static final String REQUIRES_CORE = "requires_core";
    private static final String DEPENDS_ON = "depends_on";

    private DescriptorChecks() {}

    /**
     * {@code requires_core} and every {@code depends_on[].range} must be a PEP 440 specifier set (R3, FR-002), and
     * {@code pack_version} a PEP 440 version, because a range is decided against it. An undeclared key never reaches
     * here: the closed schema makes its file unreadable (ADR-0036 decision 3).
     *
     * @param name the pack's name, for the finding text
     * @param descriptor what the pack's {@code pack.yaml} states
     * @return the findings, at {@code pack.yaml}; empty when the descriptor passes
     */
    public static List<PackFinding> findings(String name, PackDescriptor descriptor) {
        List<PackFinding> findings = new ArrayList<>();
        Optional<String> requiresCore = descriptor.requiresCore();
        if (requiresCore.isPresent() && !Pep440.isValidSpecifierSet(requiresCore.get())) {
            findings.add(new PackFinding(
                    PackFindingCode.REQUIRES_CORE_INVALID,
                    Paths.PACK_YAML,
                    List.of(REQUIRES_CORE),
                    "pack.yaml of " + name + ": requires_core '" + requiresCore.get()
                            + "' is not a PEP 440 version specifier"));
        }
        if (!Pep440Range.isVersion(descriptor.packVersion())) {
            findings.add(new PackFinding(
                    PackFindingCode.PACK_VERSION_INVALID,
                    Paths.PACK_YAML,
                    List.of("pack_version"),
                    "pack.yaml of " + name + ": pack_version '" + descriptor.packVersion()
                            + "' is not a PEP 440 version"));
        }
        List<PackDependency> dependencies = descriptor.dependencies();
        for (int i = 0; i < dependencies.size(); i++) {
            String range = dependencies.get(i).range();
            if (!Pep440.isValidSpecifierSet(range)) {
                findings.add(new PackFinding(
                        PackFindingCode.DEPENDENCY_RANGE_INVALID,
                        Paths.PACK_YAML,
                        List.of(DEPENDS_ON, Integer.toString(i), "range"),
                        "pack.yaml of " + name + ": depends_on[" + i + "] range '" + range
                                + "' is not a PEP 440 version specifier"));
            }
        }
        return findings;
    }
}
