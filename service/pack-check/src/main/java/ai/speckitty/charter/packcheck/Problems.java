package ai.speckitty.charter.packcheck;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRefusalMapping;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The problems of a pack-set verdict, each the contract's {@code Problem} as the API renders the problem of a refused
 * registration ({@code getPackRegistration}) for the same fault (#478, FR-005, T016): its title, status, code and detail,
 * its packs and at most {@value #MAX_VIOLATIONS} violations, the detail then counting those left out.
 *
 * <p>It judges nothing: the verdict says what fails, and {@link PackRefusalMapping} words a pack's violations as the
 * registration does. This class only renders, in the order of the data model: each pack's problems in the set's order
 * (its {@code validation_failed}, or the refusal of its unreadable facts, then its round trip's), then the composition
 * faults in the order {@code CLOSURE} reports them.
 */
final class Problems {

    /** The most violations a problem lists, as the contract's {@code maxItems} and the API cap them. */
    static final int MAX_VIOLATIONS = 100;

    /** The most packs a problem lists, as the API caps them. */
    static final int MAX_PACKS = 100;

    /**
     * The status the registration's problem carries for each code pack-check can emit: the {@code getPackRegistration}
     * status of the code's {@code x-refusal} entry in {@code contracts/charter/schemas/CharterRefusalCode.yaml}, that
     * operation's own where it declares one. The codes: {@code validation_failed} (a pack's violations and its round
     * trip), the refusals the composition facts' builder raises ({@code pack_unreadable}, {@code validation_failed},
     * {@code registration_conflict}), and every {@code CLOSURE} code ({@code PackCodes.CLOSURE}, its preset phase
     * included), which the registration answers as 422 in place of their own 500.
     */
    static final Map<RefusalCode, Integer> STATUS = Map.ofEntries(
            Map.entry(RefusalCode.VALIDATION_FAILED, 422),
            Map.entry(RefusalCode.PACK_UNREADABLE, 500),
            Map.entry(RefusalCode.REGISTRATION_CONFLICT, 409),
            Map.entry(RefusalCode.PACK_DEPENDENCY_CYCLE, 422),
            Map.entry(RefusalCode.PACK_DEPENDENCY_UNKNOWN, 422),
            Map.entry(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE, 422),
            Map.entry(RefusalCode.PACK_PARENT_UNRESOLVED, 422),
            Map.entry(RefusalCode.PACK_LEVEL_VIOLATION, 422),
            Map.entry(RefusalCode.PACK_NAME_CONFLICT, 422),
            Map.entry(RefusalCode.PACK_DUPLICATE_ID, 422),
            Map.entry(RefusalCode.PACK_SIBLING_CONFLICT, 422));

    private Problems() {}

    /**
     * One problem, as the contract's {@code Problem} schema holds it.
     *
     * @param title the code as a sentence
     * @param status the registration's status for the code
     * @param detail the refusal's detail, with the count of the violations left out
     * @param instance the pack's path as given, for a pack's problem; empty for a composition fault
     * @param code the refusal code's token
     * @param violations at most {@value #MAX_VIOLATIONS} violations; empty for none
     * @param packs at most {@value #MAX_PACKS} packs the fault names; empty for none
     */
    record Problem(
            String title,
            int status,
            String detail,
            Optional<String> instance,
            String code,
            List<RefusalViolation> violations,
            List<RefusalPack> packs) {
        /**
         * Checks the fields and copies the lists.
         *
         * @param title the title
         * @param status the status
         * @param detail the detail
         * @param instance the instance, possibly empty
         * @param code the code
         * @param violations the violations
         * @param packs the packs
         */
        Problem {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(instance, "instance");
            Objects.requireNonNull(code, "code");
            violations = List.copyOf(violations);
            packs = List.copyOf(packs);
        }
    }

    /**
     * The problems of a verdict, in the data model's order.
     *
     * @param verdict the pack-set verdict
     * @return the problems; empty when the set is valid
     * @throws IllegalStateException for a code the status table does not hold
     */
    static List<Problem> of(PackSetVerdict verdict) {
        List<Problem> problems = new ArrayList<>();
        for (PackResult pack : verdict.packs()) {
            problems.addAll(ofPack(pack));
        }
        for (CompositionFault fault : verdict.faults()) {
            problems.add(ofFault(fault));
        }
        return List.copyOf(problems);
    }

    /**
     * A pack's problems: its violations (validate mode's, then the set check's), or else the refusal of its facts not
     * yet reported (D3), never both; then its round trip's violations.
     */
    private static List<Problem> ofPack(PackResult pack) {
        String instance = pack.entry().root().locator();
        List<Problem> problems = new ArrayList<>();
        Rationale found = new Rationale(Stream.concat(pack.verdict().findings().stream(), pack.setCheck().stream())
                .toList());
        violationsOf(found, instance).ifPresent(problems::add);
        if (problems.isEmpty()) {
            pack.unreportedRefusal()
                    .map(refusal -> ofRefusal(refusal, instance))
                    .ifPresent(problems::add);
        }
        violationsOf(new Rationale(pack.roundTrip()), instance).ifPresent(problems::add);
        return problems;
    }

    /** The {@code validation_failed} problem of a pack's violations, in the registration's words; empty for none. */
    private static Optional<Problem> violationsOf(Rationale reasons, String instance) {
        if (reasons.violations().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ofCharterRefusal(PackRefusalMapping.of(reasons), Optional.of(instance)));
    }

    /** The problem of a pack whose composition facts cannot be read and whose verdict reports no violation. */
    private static Problem ofRefusal(PackReadout.Unreadable refusal, String instance) {
        return ofCharterRefusal(new CharterRefusal(refusal.code(), refusal.detail()), Optional.of(instance));
    }

    /** A composition fault's problem: its code, its detail and the packs it names. */
    private static Problem ofFault(CompositionFault fault) {
        String token = fault.code().value();
        RefusalCode code = Arrays.stream(RefusalCode.values())
                .filter(candidate -> candidate.token().equals(token))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(token + " is no refusal code"));
        return ofCharterRefusal(new CharterRefusal(code, fault.detail(), fault.packs()), Optional.empty());
    }

    /** A refusal rendered as the registration renders it. */
    private static Problem ofCharterRefusal(CharterRefusal refusal, Optional<String> instance) {
        return new Problem(
                title(refusal.code()),
                statusOf(refusal.code()),
                refusal.getMessage() + leftOut(refusal.violations().size()),
                instance,
                refusal.code().token(),
                refusal.violations().stream().limit(MAX_VIOLATIONS).toList(),
                refusal.packs().stream().limit(MAX_PACKS).toList());
    }

    /**
     * The registration's status for a code pack-check emits.
     *
     * @param code the code
     * @return its status
     * @throws IllegalStateException for a code the table does not hold
     */
    static int statusOf(RefusalCode code) {
        Integer status = STATUS.get(code);
        if (status == null) {
            throw new IllegalStateException(code.token() + " has no status in pack-check's table");
        }
        return status;
    }

    /**
     * The title of a code, as the API titles it: its token as a sentence ({@code pack_unreadable} is "Pack
     * unreadable"), and "Invalid DRG document" for {@code drg_schema_error}.
     *
     * @param code the code
     * @return the title
     */
    static String title(RefusalCode code) {
        if (code == RefusalCode.DRG_SCHEMA_ERROR) {
            return "Invalid DRG document";
        }
        String words = code.token().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * The sentence a detail gains when violations are left out, as the API words it; nothing when all are listed.
     *
     * @param violations how many violations the refusal holds
     * @return the sentence, with its leading space, or nothing
     */
    static String leftOut(int violations) {
        int more = violations - MAX_VIOLATIONS;
        return more > 0 ? " " + more + " more violations are not listed." : "";
    }
}
