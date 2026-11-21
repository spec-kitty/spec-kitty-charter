package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.util.ArrayList;
import java.util.List;

/**
 * How a pack fault is stated (#554, FR-001): a failing kernel {@link Reason} of its code, at its file and the place in
 * it as a JSON pointer (RFC 6901), with the detail its owner wrote. The description is the code's token. The content
 * rules, the graph binding and the one reader all state their faults through here, so a finding reads the same
 * whichever owner states it, and the pointer is encoded, and decoded, in one place.
 */
public final class Findings {

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
    public static Reason at(ReasonCode code, String file, List<String> place, String detail) {
        Location where = Location.in(file);
        if (!place.isEmpty()) {
            StringBuilder pointer = new StringBuilder();
            place.forEach(segment ->
                    pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1")));
            where = where.at(pointer.toString());
        }
        return new Reason(code.value(), new Origin(code.value()), Evaluation.FAIL, code, where, detail);
    }

    /**
     * The place a finding's pointer names: the segments of a JSON pointer (RFC 6901), decoded, the inverse of
     * {@link #at}'s encoding.
     *
     * @param pointer the pointer; empty for the whole file
     * @return the segments; none for the whole file
     */
    public static List<String> segments(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : pointer.substring(1).split("/", -1)) {
            segments.add(segment.replace("~1", "/").replace("~0", "~"));
        }
        return segments;
    }
}
