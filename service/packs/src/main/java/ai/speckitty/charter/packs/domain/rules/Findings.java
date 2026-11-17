package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.util.List;

/**
 * How a content rule states one finding (#554, FR-001): a failing kernel {@link Reason} of its code, at its file and
 * the place in it as a JSON pointer (RFC 6901), with the detail the rule wrote. The rule's description is the code's
 * token, as the one reader states its read faults, so a finding reads the same whichever owner states it.
 */
final class Findings {

    private Findings() {}

    /**
     * The finding of a fault at a file.
     *
     * @param code the code, which owns the severity
     * @param file the pack-relative file the fault belongs to
     * @param place the pointer segments inside the file; empty for the whole file
     * @param detail what the rule says about this fault
     * @return the failing reason
     */
    static Reason at(ReasonCode code, String file, List<String> place, String detail) {
        Location where = Location.in(file);
        if (!place.isEmpty()) {
            StringBuilder pointer = new StringBuilder();
            place.forEach(segment ->
                    pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1")));
            where = where.at(pointer.toString());
        }
        return new Reason(code.value(), Evaluation.FAIL, code, where, detail);
    }
}
