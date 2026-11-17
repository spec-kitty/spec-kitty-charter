package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The storage rules of the read's assembly (#554, S1, A-M1, T038). The store cannot hold a pack its read refused whole.
 * The read records that refusal as a fact of the one read ({@link Assembly.Refused}); {@link #REFUSED} states it, once,
 * where the reader's own faults do not already: a pack with no {@code pack.yaml} (R2 row 24), and an aggregate invariant
 * the readable artifacts break (row 26). A descriptor the reader could not read is the reader's fault, and a root it
 * could not read too.
 *
 * <p>The store half also meets faults of its own while it assembles a pack the read held (A-M1 rows 16 to 19 and 32,
 * WP12): a template read twice, a value-object breach, a reference that does not close, a companion it cannot read, the
 * policy file {@code mutability_policy} names, an edge that joins an artifact to itself. {@link #held} states each of
 * them once, where the read does not state the same fault already.
 */
public final class AssemblyRules {

    /** Where the store's refusal of the pack is placed when it is not the descriptor's: the pack as a whole. */
    static final String PACK = "pack";

    /** Where the store's refusal of the descriptor is placed. */
    static final String DESCRIPTOR = "pack.yaml";

    /**
     * One violation when the store refused the pack as a whole and no read fault states it: {@code pack_unreadable} at
     * {@value #DESCRIPTOR} for the descriptor's refusal, else at {@value #PACK}, the aggregate's own refusal as
     * {@link StorageCodes#ASSEMBLY_REFUSED}; in the store's words. A registration lists it right after the graph faults.
     */
    public static final Rule<TreeRead> REFUSED = AssemblyRules::refused;

    /** Every assembly rule, in this class's order. */
    public static final ModularRuleset<TreeRead> RULES =
            ModularRuleset.<TreeRead>emptyRules().must(REFUSED);

    private AssemblyRules() {}

    static List<Reason> refused(TreeRead read) {
        if (!(read.contents().assembly() instanceof Assembly.Refused refused)
                || (refused.atDescriptor()
                        && read.contents().descriptor() instanceof FileRead.Unreadable<PackDescriptor>)
                || rootUnreadable(read)) {
            return List.of();
        }
        ReasonCode code = refused.code() == RefusalCode.PACK_UNREADABLE
                ? StorageCodes.PACK_UNREADABLE
                : StorageCodes.ASSEMBLY_REFUSED;
        Reason reason = new Reason(
                code.value(),
                Evaluation.FAIL,
                code,
                Location.in(refused.atDescriptor() ? DESCRIPTOR : PACK),
                refused.message());
        return read.readFaults().contains(reason) ? List.of() : List.of(reason);
    }

    /**
     * Whether the reader already stated that the root cannot be read ({@code pack_unreadable} at {@value #PACK}): the
     * store's refusal of the same root is the same fault in its own words, whatever name the pack was read under (WP07
     * review MINOR-1, R2 row 25).
     */
    static boolean rootUnreadable(TreeRead read) {
        return read.readFaults().stream()
                .anyMatch(fault -> StorageCodes.PACK_UNREADABLE.equals(fault.code())
                        && PACK.equals(fault.location().file()));
    }

    /**
     * The file the descriptor's {@code mutability_policy} names when the store half could not read it (R2 row 32): the
     * store states that fault, in registration's words, so the reader's own fault at the same file (a parse or schema
     * fault of the policy file) is not stated beside it.
     *
     * @param read the one read
     * @return the named file, when the store could not read it
     */
    static Optional<String> unreadablePolicy(TreeRead read) {
        if (read.contents().assembly() instanceof Assembly.Assembled held
                && held.policyFault().isPresent()) {
            return held.descriptor().paths().mutabilityPolicy();
        }
        return Optional.empty();
    }

    /**
     * The store half's own faults that {@code stated} does not state already, each once, in the store's order (A-M1 rows
     * 16 to 19 and 32, WP12). They are the read's {@link Assembly.Assembled#storeOnly() store-only} faults alone, an
     * allow-list the store half builds where it meets them: a template read twice, a value-object breach, a reference or
     * an output template that does not close, a companion it cannot read, the policy file {@code mutability_policy}
     * names. The rule families the store half also judges for the composing read (the step enhancements,
     * {@code duplicate_step_id}, the prompt slots, the step links, the substep delegations) are the content rules' alone,
     * and never come from here.
     *
     * <p>A fault is suppressed only when {@code stated} holds the same fault, never because something else sits at its
     * file ({@link #sameFault}). A companion fault the reader does not state is placed at the companion's own file (R2
     * row 19, as validate mode places it); a prompt fragment's stays at the step enhancement that names it, as the
     * contract places it, and the verdict then states nothing else at the fragment ({@link #unreadFragments}).
     *
     * <p>An edge that joins an artifact to itself through the pack's own name stops the store's composition facts, which
     * the read does not see: it is stated at {@value #PACK} as {@link StorageCodes#UNREADABLE}, as the registration
     * answered it, and only when nothing else is stated, since the facts are built only for a pack the store reads
     * whole (WP12 review MINOR-4).
     *
     * @param read the one read
     * @param stated the violations the read faults and the registration's composition already state
     * @return the faults left to state
     */
    static List<Reason> held(TreeRead read, List<Reason> stated) {
        if (!(read.contents().assembly() instanceof Assembly.Assembled held)) {
            return List.of();
        }
        List<Reason> out = new ArrayList<>();
        for (PackElementViolation violation : held.storeOnly()) {
            if (stated.stream().noneMatch(reason -> sameFault(violation, reason))) {
                out.add(
                        StorageCodes.COMPANION.equals(violation.kind())
                                ? fault(violation.kind(), companionOf(violation), why(violation))
                                : fault(violation.kind(), violation.file(), violation.reason()));
            }
        }
        if (stated.isEmpty() && out.isEmpty()) {
            held.factsRefusal().ifPresent(refusal -> out.add(fault(StorageCodes.UNREADABLE, PACK, refusal)));
        }
        return out;
    }

    /**
     * Whether {@code reason} states the same fault as the store's {@code violation} (WP12 review MAJOR-1), by an explicit
     * mapping:
     *
     * <ul>
     *   <li>a companion the store cannot read is the reader's fault at the companion's own file (it does not parse, or
     *       breaks its structure), and the store does not state it again; one it finds missing, or named twice, is the
     *       reader's fault at the artifact that names the companion missing, or named more than once;
     *   <li>a prompt fragment the store cannot read is stated by the store, at the step enhancement, unless the reader
     *       states the fragment missing, or named more than once, there;
     *   <li>a mission type the store refuses (a step standing twice, a companion or an output template it cannot
     *       resolve) is the reader's one load refusal of that mission type file, which the reader words as
     *       {@code step '<id>' ...} or {@code names <path> ...};
     *   <li>any other fault is the reader's or a rule's at the same file with the same code whose words end with the
     *       store's.
     * </ul>
     *
     * @param violation the store's fault
     * @param reason a violation the read already states
     * @return whether the two are one fault
     */
    static boolean sameFault(PackElementViolation violation, Reason reason) {
        String file = reason.location().file();
        boolean ownFault = !MISSING.equals(why(violation)) && !NAMED_TWICE.equals(why(violation));
        if (StorageCodes.COMPANION.equals(violation.kind())) {
            return (file.equals(companionOf(violation)) && ownFault) || namesMissing(violation, reason);
        }
        if (PackCodes.PROMPT_FRAGMENT_INVALID.equals(violation.kind()) && namesCompanion(violation)) {
            return namesMissing(violation, reason);
        }
        if (statesMissionTypeRefusal(violation, reason)) {
            return true;
        }
        return file.equals(violation.file())
                && violation.kind().equals(reason.code())
                && reason.detail().endsWith(violation.reason());
    }

    /**
     * Whether {@code reason} is the reader's load refusal of the mission type file the store refused: the reader states
     * the first fault the store's load meets in such a file, once ({@code MissionTypeChecks}), so it is the same fault
     * as each of the store's there.
     */
    private static boolean statesMissionTypeRefusal(PackElementViolation violation, Reason reason) {
        String file = violation.file();
        if (!file.endsWith(MISSION_TYPE_FILE)
                || !reason.location().file().equals(file)
                || !StorageCodes.ARTIFACT_SCHEMA_ERROR.equals(reason.code())) {
            return false;
        }
        String detail = reason.detail().startsWith(file + ": ")
                ? reason.detail().substring(file.length() + 2)
                : reason.detail();
        return detail.startsWith("step '") || detail.startsWith("names ");
    }

    /** What a mission type's file ends with. */
    private static final String MISSION_TYPE_FILE = ".mission-type.yaml";

    /** What the store says of a companion it finds missing. */
    private static final String MISSING = "missing";

    /** What the store says of a companion two artifacts, or two entries of one, name. */
    private static final String NAMED_TWICE = "named by two artifacts";

    /**
     * Whether {@code reason} states, at the artifact, the store's companion fault in the reader's words: a companion
     * the store found missing that the reader names missing ({@code missing}, {@code does not exist}), or one named
     * twice that the reader names more than once.
     */
    private static boolean namesMissing(PackElementViolation violation, Reason reason) {
        String detail = reason.detail();
        if (!reason.location().file().equals(violation.file())
                || !(detail.contains(companionOf(violation)) || detail.contains(declaredOf(violation)))) {
            return false;
        }
        return switch (why(violation)) {
            case MISSING -> detail.endsWith(MISSING) || detail.contains("does not exist");
            case NAMED_TWICE -> detail.contains("more than once");
            default -> false;
        };
    }

    /** The store's reason of a companion fault without the companion it names: {@code <why>}. */
    static String why(PackElementViolation violation) {
        String reason = violation.reason();
        int colon = reason.indexOf(": ");
        return colon < 0 ? reason : reason.substring(colon + 2);
    }

    /** The companion path as the artifact declares it, or the reason when it names none. */
    private static String declaredOf(PackElementViolation violation) {
        int colon = violation.reason().indexOf(": ");
        return colon < 0 ? violation.reason() : violation.reason().substring(0, colon);
    }

    /**
     * The prompt fragments whose read fault the verdict states at the step enhancement that names them ({@code
     * emitted}, from {@link #held}): there the contract places it ({@code prompt-fragment-refusals.feature}, R2 row
     * 19), so the verdict states nothing else at the fragment itself. A fragment whose fault is not emitted keeps the
     * reader's faults (WP12 review MAJOR-1).
     *
     * @param emitted the store's faults the verdict states
     * @return the fragments, pack-relative
     */
    static Set<String> unreadFragments(List<Reason> emitted) {
        Set<String> out = new HashSet<>();
        for (Reason reason : emitted) {
            if (PackCodes.PROMPT_FRAGMENT_INVALID.equals(reason.code())) {
                PackElementViolation violation =
                        new PackElementViolation(reason.location().file(), reason.code(), reason.detail());
                if (namesCompanion(violation)) {
                    out.add(companionOf(violation));
                }
            }
        }
        return out;
    }

    /** Whether the store's reason starts with the companion path it names: {@code <path>: <why>}, the path one word. */
    static boolean namesCompanion(PackElementViolation violation) {
        int colon = violation.reason().indexOf(": ");
        return colon > 0 && !violation.reason().substring(0, colon).contains(" ");
    }

    /**
     * The companion a companion fault of the store names, as it words it at the artifact
     * ({@code <declared path>: <why>}), pack-relative; the artifact's own file when the reason names no path.
     */
    static String companionOf(PackElementViolation violation) {
        int colon = violation.reason().indexOf(": ");
        return colon < 0
                ? violation.file()
                : sibling(violation.file(), violation.reason().substring(0, colon));
    }

    /**
     * The pack-relative file a path declared relative to {@code file} names: its directory joined with the path,
     * {@code .} and {@code ..} segments resolved. A path that leaves the pack keeps its {@code ..} segments.
     */
    static String sibling(String file, String declared) {
        Deque<String> segments = new ArrayDeque<>();
        String[] own = file.split("/", -1);
        for (int i = 0; i < own.length - 1; i++) {
            segments.addLast(own[i]);
        }
        for (String segment : declared.split("/", -1)) {
            if (segment.equals("..")
                    && !segments.isEmpty()
                    && !segments.peekLast().equals("..")) {
                segments.removeLast();
            } else if (!segment.equals(".") && !segment.isEmpty()) {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }

    private static Reason fault(ReasonCode code, String file, String detail) {
        return new Reason(code.value(), Evaluation.FAIL, code, Location.in(file), detail);
    }

    /**
     * Whether a reason is one {@link #REFUSED} states.
     *
     * @param reason a reason
     * @return true for the store's refusal of the pack as a whole
     */
    static boolean statesRefusal(Reason reason) {
        String file = reason.location().file();
        return (StorageCodes.PACK_UNREADABLE.equals(reason.code())
                        || StorageCodes.ASSEMBLY_REFUSED.equals(reason.code()))
                && (PACK.equals(file) || DESCRIPTOR.equals(file));
    }
}
