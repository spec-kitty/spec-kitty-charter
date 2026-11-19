package ai.speckitty.charter.packcheck;

import ai.speckitty.charter.packcheck.Arguments.Help;
import ai.speckitty.charter.packcheck.Arguments.Outcome;
import ai.speckitty.charter.packcheck.Arguments.Parsed;
import ai.speckitty.charter.packcheck.Arguments.UsageFault;
import ai.speckitty.charter.packcheck.Problems.Problem;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

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
 * {@code 2} on a usage fault, a failure outside a pack read (the run directory, writing the report) or an internal
 * error ({@code pack-check: internal error: ...} and its stack trace on standard error), with nothing on standard
 * output. The report is built whole before it is written, so no partial report reaches standard output.
 *
 * <p>The run directory is deleted in a {@code finally}, so a failure midway leaves none behind. No shutdown hook covers
 * an interrupted JVM (SIGINT, SIGTERM): a run stopped that way can leave its {@code pack-check-*} directory under the
 * temporary directory.
 */
public final class PackCheckMain {

    /** No problem: the set is valid, warnings allowed. */
    static final int VALID = 0;

    /** At least one problem. */
    static final int PROBLEMS = 1;

    /** A usage fault, a failure outside a pack read, or an internal error. */
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
     * Runs pack-check, its temporary directories under the JVM's temporary directory. Public so the contract test
     * ({@code PackCheckReportContractTest}, in {@code deployable}) and the parity test run the CLI in process, as
     * {@link #main} does, without exiting the JVM.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @return the exit code
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
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
        return run(args, out, err, temporary, new Composition(new FileSystemPackInspector(), PackRoundTrip::run));
    }

    /**
     * What the composition root plugs into the pack-set check: the reader and the production round trip. A test gives
     * its own to reach a failure no canonical pack provokes.
     *
     * @param reading the port that reads one pack root once
     * @param flow the production round trip of one pack, from its directory into an empty target
     */
    record Composition(PackReading reading, BiFunction<Path, Path, PackRoundTripResult> flow) {
        /**
         * Checks the fields.
         *
         * @param reading the reader
         * @param flow the round trip
         */
        Composition {
            Objects.requireNonNull(reading, "reading");
            Objects.requireNonNull(flow, "flow");
        }
    }

    /**
     * Runs pack-check over a composition.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @param temporary the directory the round trip's run directory is made in
     * @param composition the reader and the round trip
     * @return the exit code
     */
    static int run(String[] args, PrintStream out, PrintStream err, Path temporary, Composition composition) {
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
            case Parsed parsed -> check(parsed, out, err, temporary, composition);
        };
    }

    /**
     * The check, its report built whole before anything reaches standard output: an I/O failure outside a pack read, or
     * any other failure (an internal error), exits {@link #FAILED} with nothing on standard output, so {@link #PROBLEMS}
     * always means a report that lists problems.
     */
    private static int check(Parsed parsed, PrintStream out, PrintStream err, Path temporary, Composition composition) {
        List<Problem> problems;
        List<String> warnings;
        String report;
        try {
            PackSetVerdict verdict = verdict(parsed, temporary, composition);
            problems = Problems.of(verdict);
            warnings = ReportWriter.warnings(verdict);
            report = ReportWriter.report(verdict, problems);
        } catch (UncheckedIOException failure) {
            err.println("pack-check: " + failure.getMessage());
            return FAILED;
        } catch (RuntimeException failure) {
            err.println("pack-check: internal error: " + failure);
            failure.printStackTrace(err);
            return FAILED;
        }
        warnings.forEach(err::println);
        out.print(report);
        int written = written(out, err);
        if (written != VALID) {
            return written;
        }
        return problems.isEmpty() ? VALID : PROBLEMS;
    }

    /** The verdict of the set; with the round trip, its run directory is gone when this returns. */
    private static PackSetVerdict verdict(Parsed parsed, Path temporary, Composition composition) {
        PackSetCheck check = new PackSetCheck(composition.reading());
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
                    root -> composition
                            .flow()
                            .apply(Path.of(root.locator()), run.resolve(next.incrementAndGet() + "-" + root.name())));
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
