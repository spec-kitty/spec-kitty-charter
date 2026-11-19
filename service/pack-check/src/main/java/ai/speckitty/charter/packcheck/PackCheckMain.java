package ai.speckitty.charter.packcheck;

import ai.speckitty.charter.packcheck.Arguments.Help;
import ai.speckitty.charter.packcheck.Arguments.Outcome;
import ai.speckitty.charter.packcheck.Arguments.Parsed;
import ai.speckitty.charter.packcheck.Arguments.UsageFault;
import ai.speckitty.charter.packcheck.Problems.Problem;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import ai.speckitty.charter.packvalidation.roundtrip.RoundTripping;
import ai.speckitty.charter.packvalidation.set.PackSetCheck;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The pack-check CLI (ADR-0075 decision 9, #478, FR-001, FR-006, T017): the composition root of the pack-set check.
 *
 * <p>It plugs the file-system reader ({@link FileSystemPackInspector}, the {@code PackReading} port) into
 * {@link PackSetCheck}, and, with {@code --round-trip}, the production round trip ({@link PackRoundTrip}) as the
 * {@link RoundTripping} port, each pack written into its own temporary directory under one run directory that is deleted
 * before the report is written. It holds no check: the verdict is {@code pack-validation}'s, and this class only renders
 * it.
 *
 * <p>Exit codes: {@code 0} when the report holds no problem (warnings allowed), {@code 1} when it holds one, and
 * {@code 2} on a usage fault or a failure outside a pack read (the run directory, writing the report), with nothing on
 * standard output.
 */
public final class PackCheckMain {

    /** No problem: the set is valid, warnings allowed. */
    static final int VALID = 0;

    /** At least one problem. */
    static final int PROBLEMS = 1;

    /** A usage fault, or a failure outside a pack read. */
    static final int FAILED = 2;

    private PackCheckMain() {}

    /**
     * Runs pack-check and exits with its code.
     *
     * @param args the command line
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Runs pack-check, its temporary directories under the JVM's temporary directory.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @return the exit code
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * Runs pack-check.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @param temporary the directory the round trip's run directory is made in
     * @return the exit code
     */
    static int run(String[] args, PrintStream out, PrintStream err, Path temporary) {
        Outcome outcome = Arguments.parse(args);
        return switch (outcome) {
            case Help help -> {
                out.print(Arguments.USAGE);
                yield written(out, err);
            }
            case UsageFault fault -> {
                err.println("pack-check: " + fault.message());
                err.print(Arguments.USAGE);
                yield FAILED;
            }
            case Parsed parsed -> check(parsed, out, err, temporary);
        };
    }

    private static int check(Parsed parsed, PrintStream out, PrintStream err, Path temporary) {
        PackSetVerdict verdict;
        try {
            verdict = verdict(parsed, temporary);
        } catch (UncheckedIOException failure) {
            err.println("pack-check: " + failure.getMessage());
            return FAILED;
        }
        List<Problem> problems = Problems.of(verdict);
        ReportWriter.warnings(verdict).forEach(err::println);
        out.print(ReportWriter.report(verdict, problems));
        int written = written(out, err);
        if (written != VALID) {
            return written;
        }
        return problems.isEmpty() ? VALID : PROBLEMS;
    }

    /** The verdict of the set; with the round trip, its run directory is gone when this returns. */
    private static PackSetVerdict verdict(Parsed parsed, Path temporary) {
        PackSetCheck check = new PackSetCheck(new FileSystemPackInspector());
        if (!parsed.roundTrip()) {
            return check.check(parsed.packs());
        }
        Path run;
        try {
            run = Files.createTempDirectory(temporary, "pack-check-");
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot make the round trip's temporary directory", failure);
        }
        try {
            AtomicInteger next = new AtomicInteger();
            return check.checkWithRoundTrip(
                    parsed.packs(),
                    root -> PackRoundTrip.run(
                            Path.of(root.locator()), run.resolve(next.incrementAndGet() + "-" + root.name())));
        } finally {
            delete(run);
        }
    }

    /** {@link #VALID} when standard output took everything written to it, else {@link #FAILED}, said on {@code err}. */
    private static int written(PrintStream out, PrintStream err) {
        out.flush();
        if (out.checkError()) {
            err.println("pack-check: cannot write to standard output");
            return FAILED;
        }
        return VALID;
    }

    /** Deletes a directory tree, its links unfollowed. */
    private static void delete(Path directory) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot delete the round trip's temporary directory " + directory, failure);
        }
    }
}
