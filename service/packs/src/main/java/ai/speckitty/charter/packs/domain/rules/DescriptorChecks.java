package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.Pep440;
import ai.speckitty.charter.kernel.domain.Pep440Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The descriptor's well-formedness: a pure format function over the values {@code pack.yaml} states (FR-004). The one
 * reader states each fault as a precheck under the code its storage edge builds (S2, FR-017), the offering refuses on
 * the first, and the resolver takes only a descriptor with none, so one malformed range gets one answer, whichever path
 * reads the pack. The function names no code: which code a precheck answers is the reader's.
 */
public final class DescriptorChecks {

    private static final String REQUIRES_CORE = "requires_core";
    private static final String DEPENDS_ON = "depends_on";

    private DescriptorChecks() {}

    /** Which value of the descriptor a fault is about: the reader codes each apart. */
    public enum Check {
        /** {@code requires_core} is not a PEP 440 specifier set. */
        REQUIRES_CORE,
        /** {@code pack_version} is not a PEP 440 version. */
        PACK_VERSION,
        /** A {@code depends_on[].range} is not a PEP 440 specifier set. */
        DEPENDENCY_RANGE
    }

    /**
     * One malformed value of a descriptor, at {@code pack.yaml}.
     *
     * @param check which value it is
     * @param place the pointer segments of the value inside {@code pack.yaml}
     * @param detail the sentence for the reader, naming the pack
     */
    public record Fault(Check check, List<String> place, String detail) {
        /**
         * Checks the fields and copies the place.
         *
         * @param check which value it is
         * @param place the pointer segments
         * @param detail the sentence
         */
        public Fault {
            Objects.requireNonNull(check, "check");
            Objects.requireNonNull(detail, "detail");
            place = List.copyOf(place);
        }
    }

    /**
     * {@code requires_core} and every {@code depends_on[].range} must be a PEP 440 specifier set (R3, FR-002), and
     * {@code pack_version} a PEP 440 version, because a range is decided against it. An undeclared key never reaches
     * here: the closed schema makes its file unreadable (ADR-0036 decision 3).
     *
     * @param name the pack's name, for the fault's text
     * @param descriptor what the pack's {@code pack.yaml} states
     * @return the faults, in declaration order; empty when the descriptor passes
     */
    public static List<Fault> faults(String name, PackDescriptor descriptor) {
        List<Fault> faults = new ArrayList<>();
        Optional<String> requiresCore = descriptor.requiresCore();
        if (requiresCore.isPresent() && !Pep440.isValidSpecifierSet(requiresCore.get())) {
            faults.add(new Fault(
                    Check.REQUIRES_CORE,
                    List.of(REQUIRES_CORE),
                    "pack.yaml of " + name + ": requires_core '" + requiresCore.get()
                            + "' is not a PEP 440 version specifier"));
        }
        if (!Pep440Range.isVersion(descriptor.packVersion())) {
            faults.add(new Fault(
                    Check.PACK_VERSION,
                    List.of("pack_version"),
                    "pack.yaml of " + name + ": pack_version '" + descriptor.packVersion()
                            + "' is not a PEP 440 version"));
        }
        List<PackDependency> dependencies = descriptor.dependencies();
        for (int i = 0; i < dependencies.size(); i++) {
            String range = dependencies.get(i).range();
            if (!Pep440.isValidSpecifierSet(range)) {
                faults.add(new Fault(
                        Check.DEPENDENCY_RANGE,
                        List.of(DEPENDS_ON, Integer.toString(i), "range"),
                        "pack.yaml of " + name + ": depends_on[" + i + "] range '" + range
                                + "' is not a PEP 440 version specifier"));
            }
        }
        return faults;
    }
}
