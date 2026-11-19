package ai.speckitty.charter.packcheck;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;

/**
 * Test-only access to pack-check's package-private rendering, for {@code PackCheckReportContractTest} in
 * {@code deployable.bdd}, which compares it with the API's {@code RefusalHandler} (#478, T021). It sits in pack-check's
 * package on deployable's test classpath, so pack-check opens nothing of its own for a test: no production class names
 * it, and it holds no rendering, only calls.
 */
public final class PackCheckSeam {

    private PackCheckSeam() {}

    /**
     * The codes pack-check's status table holds: every code it can emit.
     *
     * @return the codes
     */
    public static Set<RefusalCode> statusTableCodes() {
        return Problems.STATUS.keySet();
    }

    /**
     * The title pack-check gives a code.
     *
     * @param code the code
     * @return its title
     */
    public static String title(RefusalCode code) {
        return Problems.title(code);
    }

    /**
     * The status pack-check gives a code.
     *
     * @param code the code
     * @return its status
     * @throws IllegalStateException for a code its table does not hold
     */
    public static int status(RefusalCode code) {
        return Problems.statusOf(code);
    }

    /**
     * Error handling: runs pack-check with the real reader, except that every pack's composition facts are unreadable
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
        PackCheckMain.Composition noFacts = new PackCheckMain.Composition(
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
        return PackCheckMain.run(args, out, err, temporary, noFacts);
    }
}
