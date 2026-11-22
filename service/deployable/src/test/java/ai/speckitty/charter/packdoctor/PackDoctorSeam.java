package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;

/**
 * Test-only access to pack-doctor's package-private rendering, for {@code PackDoctorReportContractTest} in
 * {@code deployable.bdd}, which compares it with the API's {@code RefusalHandler} (#478, T021). It sits in pack-doctor's
 * package on deployable's test classpath, so pack-doctor opens nothing of its own for a test: no production class names
 * it, and it holds no rendering, only calls; its runs plug test controls into the CLI's composition root
 * for the error-handling branches no canonical pack reaches.
 */
public final class PackDoctorSeam {

    private PackDoctorSeam() {}

    /**
     * The codes pack-doctor's status table holds: every code it can emit.
     *
     * @return the codes
     */
    public static Set<RefusalCode> statusTableCodes() {
        return Problems.STATUS.keySet();
    }

    /**
     * The title pack-doctor gives a code.
     *
     * @param code the code
     * @return its title
     */
    public static String title(RefusalCode code) {
        return Problems.title(code);
    }

    /**
     * The status pack-doctor gives a code.
     *
     * @param code the code
     * @return its status
     * @throws IllegalStateException for a code its table does not hold
     */
    public static int status(RefusalCode code) {
        return Problems.statusOf(code);
    }

    /**
     * Error handling: runs pack-doctor with the real reader, except that every pack's composition facts are unreadable
     * ({@link PackReadout.Unreadable#noAssembly}), as no canonical pack makes them, so the D3 branch that renders the
     * refusal of a pack with no violation is reached in process.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @param temporary the round trip's temporary directory
     * @return the exit code
     */
    public static int runWithUnreadableFacts(String[] args, PrintStream out, PrintStream err, Path temporary) {
        FileSystemPackInspector inspector = new FileSystemPackInspector();
        PackDoctorMain.Composition noFacts = new PackDoctorMain.Composition(
                root -> {
                    PackReadout read = inspector.read(root);
                    return new PackReadout(
                            read.contents(),
                            read.files(),
                            read.notHeld(),
                            read.rationale(),
                            PackReadout.Unreadable.noAssembly(root.name()));
                },
                PackRoundTrip::run);
        return PackDoctorMain.run(args, out, err, temporary, noFacts);
    }

    /**
     * Error handling: runs pack-doctor with the real reader and the given round trip in place of the production one, so
     * a positive control of the round trip ({@code RoundTripControls}, a loss planted on the write path) turns a
     * canonical pack red, as no canonical pack does through the production flow.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @param temporary the round trip's temporary directory
     * @param flow the round trip of one pack, under its registered id, from its directory into an empty target
     * @return the exit code
     */
    public static int runWithRoundTrip(
            String[] args, PrintStream out, PrintStream err, Path temporary, ControlFlow flow) {
        return PackDoctorMain.run(
                args, out, err, temporary, new PackDoctorMain.Composition(new FileSystemPackInspector(), flow::run));
    }

    /** A round trip of one pack in place of the production one, for a test outside this package. */
    @FunctionalInterface
    public interface ControlFlow {

        /**
         * Round-trips one pack.
         *
         * @param packId the registered id the pack is configured under
         * @param source the pack directory
         * @param target an empty directory that becomes the written pack
         * @return what the round trip observed
         */
        PackRoundTripResult run(PackIdentifier packId, Path source, Path target);
    }
}
