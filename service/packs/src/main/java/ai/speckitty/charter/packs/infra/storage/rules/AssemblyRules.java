package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
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
     * 16 to 19 and 32, WP12). A fault is stated already when a violation of {@code stated} sits at its file, or, for a
     * companion the store could not read ({@link StorageCodes#COMPANION}), at the companion's own file: the reader and
     * the content rules speak first, in their words. One the read does not state is placed at its file, a companion's at
     * the companion's own file (R2 row 19, as validate mode places it), with the store's code and words. An edge that
     * joins an artifact to itself through its pack's own qualifier stops the store's composition facts, and the read
     * does not see it: it is stated at {@value #PACK} as {@link StorageCodes#UNREADABLE}, as the registration answers it.
     *
     * @param read the one read
     * @param stated the violations the read faults and the registration's composition already state
     * @return the faults left to state
     */
    static List<Reason> held(TreeRead read, List<Reason> stated) {
        if (!(read.contents().assembly() instanceof Assembly.Assembled held)) {
            return List.of();
        }
        Set<String> files = new HashSet<>();
        stated.forEach(reason -> files.add(reason.location().file()));
        List<Reason> out = new ArrayList<>();
        for (PackElementViolation violation : held.violations()) {
            if (StorageCodes.COMPANION.equals(violation.kind())) {
                companionFault(violation, files).ifPresent(out::add);
            } else if (!files.contains(violation.file())) {
                out.add(fault(violation.kind(), violation.file(), violation.reason()));
            }
        }
        if (stated.isEmpty() && out.isEmpty()) {
            held.factsRefusal().ifPresent(refusal -> out.add(fault(StorageCodes.UNREADABLE, PACK, refusal)));
        }
        return out;
    }

    /**
     * A companion fault, as the store words it at the artifact ({@code <declared path>: <why>}), placed at the
     * companion's own file; empty when a violation sits at the artifact or at the companion already.
     */
    static Optional<Reason> companionFault(PackElementViolation violation, Set<String> stated) {
        String reason = violation.reason();
        int colon = reason.indexOf(": ");
        String companion = colon < 0 ? violation.file() : sibling(violation.file(), reason.substring(0, colon));
        if (stated.contains(violation.file()) || stated.contains(companion)) {
            return Optional.empty();
        }
        String why = colon < 0 ? reason : reason.substring(colon + 2);
        return Optional.of(fault(violation.kind(), companion, why));
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
