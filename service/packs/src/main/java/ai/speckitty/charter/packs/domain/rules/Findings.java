package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.util.ArrayList;
import java.util.List;

/**
 * How a pack fault is stated (#554, FR-001): a failing kernel {@link Reason} of its code, at its file and the place in
 * it as a JSON pointer (RFC 6901), with the detail its owner wrote. The description is the code's token. The content
 * rules, the graph binding and the one reader all state their faults through here, so a finding reads the same
 * whichever owner states it, and the pointer is encoded, and decoded, in one place. A finding judges one pack's file,
 * which its {@link Location} names; it is about no element the owner can name, so its origin is {@link Origin#NONE}.
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
        return new Reason(
                code.value(),
                Origin.NONE,
                Evaluation.FAIL,
                code,
                Location.in(file).at(pointer(place)),
                detail);
    }

    /**
     * The finding of a pack violation, at its file and the place in it the violation names.
     *
     * @param violation the violation
     * @return the failing reason, in the violation's words
     */
    public static Reason of(PackElementViolation violation) {
        return new Reason(
                violation.kind().value(),
                Origin.NONE,
                Evaluation.FAIL,
                violation.kind(),
                Location.in(violation.file()).at(violation.pointer()),
                violation.reason());
    }

    /**
     * The violation of a fault at an edge of a graph document: {@code /edges/<index>} in it.
     *
     * @param file the graph document
     * @param index the edge's index in the document's {@code edges} list
     * @param code the code
     * @param reason the reason
     * @return the violation at the edge
     */
    public static PackElementViolation atEdge(String file, int index, ReasonCode code, String reason) {
        return new PackElementViolation(
                file, pointer(List.of(GraphBinding.EDGES, Integer.toString(index))), code, reason);
    }

    /**
     * The JSON pointer (RFC 6901) of a place: each segment escaped ({@code ~} as {@code ~0}, {@code /} as {@code ~1}),
     * the inverse of {@link #segments}.
     *
     * @param place the segments; none for the whole file
     * @return the pointer; empty for the whole file
     */
    public static String pointer(List<String> place) {
        StringBuilder pointer = new StringBuilder();
        place.forEach(
                segment -> pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1")));
        return pointer.toString();
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
