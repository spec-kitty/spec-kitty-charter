package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packdoctor.Arguments.Help;
import ai.speckitty.charter.packdoctor.Arguments.Outcome;
import ai.speckitty.charter.packdoctor.Arguments.Parsed;
import ai.speckitty.charter.packdoctor.Arguments.UsageFault;
import ai.speckitty.charter.packdoctor.Problems.Problem;
import ai.speckitty.charter.packdoctor.report.ReportFile;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackRemediation;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import ai.speckitty.charter.packvalidation.roundtrip.RoundTripping;
import ai.speckitty.charter.packvalidation.set.PackSetCheck;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

/**
 * The pack-doctor CLI (ADR-0075 decision 9, #478, FR-001, FR-006, T017): the composition root of the pack-set check,
 * which its {@code validate} command runs, and of the remediation its {@code remediate} command runs (FR-009 of mission
 * pack-doctor-coverage-01M7YMQK, plan D7).
 *
 * <p>It plugs the file-system reader ({@link FileSystemPackInspector}, the {@code PackReading} port) into
 * {@link PackSetCheck}, and, with {@code --round-trip}, the production round trip ({@link PackRoundTrip}) as the
 * {@link RoundTripping} port, each pack written into its own temporary directory under one run directory that is deleted
 * before the report is written. It holds no check: the verdict is {@code pack-validation}'s, and this class only renders
 * it.
 *
 * <p>Exit codes: {@code 0} when the report holds no problem (warnings allowed), {@code 1} when it holds one, and
 * {@code 2} on a usage fault, a failure outside a pack read (the run directory, writing the report) or an internal
 * error ({@code pack-doctor: internal error: ...} and its stack trace on standard error), with nothing on standard
 * output. The report is built whole before it is written, so no partial report reaches standard output.
 *
 * <p>With {@code --report <file>} (FR-008 of mission pack-doctor-coverage-01M7YMQK), the same report is written to that
 * file too, as YAML or XML (a string XML 1.0 cannot carry escaped, ruling R-ac). It is written in a hidden file beside
 * the file ({@code .<name>.<uuid>.tmp}) before anything reaches standard output, so a file that cannot be written
 * exits {@code 2} with nothing on standard output; once standard output has taken the report, it replaces the file in
 * one rename, so a reader never sees half a report. When standard output cannot be written, the run exits {@code 2}
 * and the file is left as it was, so it never names an exit code the run did not exit with; only a rename that fails
 * after standard output took the report exits {@code 2} with the report on standard output. A {@code --report} that is
 * a link is followed and the file it names replaced. Writing it never changes the exit code of a run, and standard
 * output is the same with it as without.
 *
 * <p>{@code remediate} plugs the storage edge's {@link PackRemediation} into {@link Remediate}, which prints each pack's
 * outcome on standard output ({@code rewritten}, {@code unchanged}, or {@code refused} and its faults), then runs the
 * check of the same set with the round trip and writes its report after those lines. It exits {@code 0} when no pack was
 * refused and the check holds no problem, {@code 1} when a pack was refused or the check holds a problem, and {@code 2}
 * on a usage fault or a failure, the outcome lines already printed left on standard output: a pack that fails while it
 * is remediated is as it was, which standard error says ({@code it is unchanged}), unless it was moved aside and could
 * not be put back, when standard error names the hidden aside copy that holds it, then the failure and every failure
 * suppressed on it; the packs before it keep what was done to them. A pack that is or holds the working directory is a
 * usage fault.
 *
 * <p>Pack content never reaches the internal-error path (#624): every fault a pack holds, a malformed {@code pack.yaml}
 * field included, is a problem ({@code 1}), and {@code CLOSURE} judges only descriptors the read accepted. An internal
 * error is a defect of pack-doctor. It exits {@code 2}, not {@code 1}, because no report backs it; a throw left to the
 * JVM would exit {@code 1} with nothing on standard output.
 *
 * <p>The run directory is deleted in a {@code finally}, so a failure midway leaves none behind. No shutdown hook covers
 * an interrupted JVM (SIGINT, SIGTERM): a run stopped that way can leave its {@code pack-doctor-*} directory under the
 * temporary directory.
 */
public final class PackDoctorMain {

    /** No problem: the set is valid, warnings allowed. */
    static final int VALID = 0;

    /** At least one problem. */
    static final int PROBLEMS = 1;

    /** A usage fault, a failure outside a pack read, or an internal error. */
    static final int FAILED = 2;

    private PackDoctorMain() {}

    /**
     * Runs pack-doctor and exits with its code.
     *
     * @param args the command line
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Runs pack-doctor, its temporary directories under the JVM's temporary directory. Public so the contract test
     * ({@code PackDoctorReportContractTest}, in {@code deployable}) and the parity test run the CLI in process, as
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
     * Runs pack-doctor.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @param temporary the directory the round trip's run directory is made in
     * @return the exit code
     */
    static int run(String[] args, PrintStream out, PrintStream err, Path temporary) {
        return run(
                args,
                out,
                err,
                temporary,
                new Composition(new FileSystemPackInspector(), PackRoundTrip::run, PackDoctorMain::remediated));
    }

    /**
     * The production round trip of one pack ({@link PackRoundTrip#run(PackIdentifier, Path, Path)}): the pack in
     * {@code source}, configured under {@code packId}, written into the empty {@code target}.
     */
    @FunctionalInterface
    interface Flow {
        /**
         * Round-trips one pack.
         *
         * @param packId the registered id the pack is configured under: its {@code --pack}'s {@code =<id>}, or its
         *     directory's name
         * @param source the pack directory
         * @param target an empty or missing directory that becomes the written pack
         * @return what the round trip observed
         */
        PackRoundTripResult run(PackIdentifier packId, Path source, Path target);
    }

    /**
     * What the composition root plugs into the pack-set check and the remediation: the reader, the production round trip
     * and the storage edge's remediation. A test gives its own to reach a failure no canonical pack provokes.
     *
     * @param reading the port that reads one pack root once
     * @param flow the production round trip of one pack, under its id, from its directory into an empty target
     * @param repair the remediation of one pack
     */
    record Composition(PackReading reading, Flow flow, Remediate.Repair repair) {
        /**
         * Checks the fields.
         *
         * @param reading the reader
         * @param flow the round trip
         * @param repair the remediation
         */
        Composition {
            Objects.requireNonNull(reading, "reading");
            Objects.requireNonNull(flow, "flow");
            Objects.requireNonNull(repair, "repair");
        }

        /**
         * A reader and a round trip of the caller's, with the production remediation.
         *
         * @param reading the reader
         * @param flow the round trip
         */
        Composition(PackReading reading, Flow flow) {
            this(reading, flow, PackDoctorMain::remediated);
        }
    }

    /** The production remediation of one pack ({@link PackRemediation}), its outcome in the command's words. */
    private static Remediate.Result remediated(PackRoot root) {
        return switch (new PackRemediation().remediate(root)) {
            case PackRemediation.Rewritten rewritten -> new Remediate.Rewritten();
            case PackRemediation.Unchanged unchanged -> new Remediate.Unchanged();
            case PackRemediation.Refused refused -> new Remediate.Refused(refused.violations());
        };
    }

    /**
     * Runs pack-doctor over a composition.
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @param temporary the directory the round trip's run directory is made in
     * @param composition the reader and the round trip
     * @return the exit code
     */
    static int run(String[] args, PrintStream out, PrintStream err, Path temporary, Composition composition) {
        return run(args, out, err, temporary, composition, Arguments::parse);
    }

    /**
     * Runs pack-doctor with the given argument reader; the seam lets a test prove that a failure while reading the
     * arguments exits {@link #FAILED}, never {@link #PROBLEMS} (PR #608 review L5).
     *
     * @param args the command line
     * @param out where the report goes
     * @param err where the usage, the warnings and the failures go
     * @param temporary the directory the round trip's run directory is made in
     * @param composition the reader and the round trip
     * @param parser what reads the command line, {@link Arguments#parse} outside tests
     * @return the exit code
     */
    static int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Path temporary,
            Composition composition,
            Function<String[], Outcome> parser) {
        Outcome outcome;
        try {
            outcome = parser.apply(args);
        } catch (RuntimeException failure) {
            // An internal error while reading the arguments exits FAILED too, never PROBLEMS (PR #608 review MEDIUM-B).
            err.println("pack-doctor: internal error: " + failure);
            failure.printStackTrace(err);
            return FAILED;
        }
        return switch (outcome) {
            case Help help -> {
                out.print(Arguments.USAGE);
                yield written(out, err);
            }
            case UsageFault fault -> {
                err.println("pack-doctor: " + fault.message());
                err.print(Arguments.USAGE);
                yield FAILED;
            }
            case Arguments.Remediate remediate -> remediate(remediate, out, err, temporary, composition);
            case Parsed parsed -> check(parsed, out, err, temporary, composition);
        };
    }

    /**
     * The remediation of each pack, its outcome printed, then the check of the same set with the round trip: {@link
     * #PROBLEMS} when a pack was refused, else the check's code. A failure while a pack is remediated exits {@link
     * #FAILED} before the check.
     */
    private static int remediate(
            Arguments.Remediate remediate, PrintStream out, PrintStream err, Path temporary, Composition composition) {
        boolean refused;
        try {
            refused = Remediate.run(remediate.validation().packs(), composition.repair(), out);
        } catch (UncheckedIOException failure) {
            // The message says whether the pack is as it was or where its aside copy is; the cause says why, and a
            // failure suppressed on either (a put-back that failed too) is said after it.
            err.println("pack-doctor: " + failure.getMessage() + ": " + failure.getCause());
            Stream.concat(
                            Stream.of(failure.getSuppressed()),
                            Stream.ofNullable(failure.getCause()).flatMap(cause -> Stream.of(cause.getSuppressed())))
                    .forEach(suppressed -> err.println("pack-doctor:   and: " + suppressed));
            return FAILED;
        } catch (IllegalStateException failure) {
            err.println("pack-doctor: " + failure.getMessage());
            return FAILED;
        } catch (RuntimeException failure) {
            err.println("pack-doctor: internal error: " + failure);
            failure.printStackTrace(err);
            return FAILED;
        }
        int checked = check(remediate.validation(), out, err, temporary, composition);
        return checked == VALID && refused ? PROBLEMS : checked;
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
        Optional<String> file;
        try {
            PackSetVerdict verdict = verdict(parsed, temporary, composition);
            problems = Problems.of(verdict);
            warnings = ReportWriter.warnings(verdict);
            JsonNode tree = ReportWriter.tree(parsed, verdict, problems);
            report = ReportWriter.json(tree);
            file = parsed.report().map(target -> fileText(target, tree));
        } catch (UnrepresentableReport failure) {
            err.println("pack-doctor: " + failure.getMessage());
            return FAILED;
        } catch (UncheckedIOException failure) {
            err.println("pack-doctor: " + failure.getMessage());
            return FAILED;
        } catch (RuntimeException failure) {
            err.println("pack-doctor: internal error: " + failure);
            failure.printStackTrace(err);
            return FAILED;
        }
        Optional<StagedReport> staged = Optional.empty();
        if (file.isPresent()) {
            staged = staged(parsed.report().orElseThrow(), file.get(), err);
            if (staged.isEmpty()) {
                return FAILED;
            }
        }
        warnings.forEach(err::println);
        out.print(report);
        int written = written(out, err);
        if (written != VALID) {
            // The file would name an exit code this run does not exit with: it is left as it was (squad B8).
            staged.ifPresent(PackDoctorMain::discard);
            return written;
        }
        if (staged.isPresent() && !committed(staged.get(), err)) {
            return FAILED;
        }
        return problems.isEmpty() ? VALID : PROBLEMS;
    }

    /** A report the file's format cannot carry: a usage-level failure, not an internal error. */
    private static final class UnrepresentableReport extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnrepresentableReport(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The report file's text, built before anything is written. */
    private static String fileText(ReportFile target, JsonNode tree) {
        try {
            return target.format().write(tree);
        } catch (IllegalArgumentException unrepresentable) {
            throw new UnrepresentableReport(
                    "cannot write the report " + target.path() + " as "
                            + target.format().name() + ": " + unrepresentable.getMessage(),
                    unrepresentable);
        }
    }

    /**
     * The report file written in full beside the file it replaces, not yet in its place.
     *
     * @param given the {@code --report} path, as the user gave it
     * @param staged the hidden file that holds the report
     * @param destination the file it replaces: the given path, or the file a link there names
     */
    private record StagedReport(Path given, Path staged, Path destination) {}

    /** The report's text in a hidden file beside its destination; empty, said on {@code err}, when it cannot be. */
    private static Optional<StagedReport> staged(ReportFile target, String text, PrintStream err) {
        Path given = target.path();
        Path staged = null;
        try {
            Path destination = Files.isSymbolicLink(given) ? given.toRealPath() : given;
            Path name = destination.getFileName();
            if (name == null || Files.isDirectory(destination)) {
                throw new IOException(destination + " is a directory, not a file");
            }
            staged = destination.resolveSibling("." + name + "." + UUID.randomUUID() + ".tmp");
            Files.writeString(staged, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return Optional.of(new StagedReport(given, staged, destination));
        } catch (IOException failure) {
            err.println("pack-doctor: cannot write the report " + given + ": " + failure);
            if (staged != null) {
                discard(new StagedReport(given, staged, staged));
            }
            return Optional.empty();
        }
    }

    /** The staged report moved into its place in one rename; false, said on {@code err}, when it cannot be. */
    private static boolean committed(StagedReport report, PrintStream err) {
        try {
            try {
                Files.move(
                        report.staged(),
                        report.destination(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(report.staged(), report.destination(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException failure) {
            err.println("pack-doctor: cannot write the report " + report.given() + ": " + failure);
            discard(report);
            return false;
        }
    }

    /** Deletes a staged report; one that cannot be deleted is a hidden file the next run does not read. */
    private static void discard(StagedReport report) {
        try {
            Files.deleteIfExists(report.staged());
        } catch (IOException ignored) {
            // Left beside the report, hidden; it never changes what the run answered.
        }
    }

    /** The verdict of the set; with the round trip, its run directory is gone when this returns. */
    private static PackSetVerdict verdict(Parsed parsed, Path temporary, Composition composition) {
        PackSetCheck check = new PackSetCheck(composition.reading());
        if (!parsed.roundTrip()) {
            return parsed.full() ? check.checkFull(parsed.packs()) : check.check(parsed.packs());
        }
        Path run;
        try {
            run = Files.createTempDirectory(temporary, "pack-doctor-");
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot make the round trip's temporary directory", failure);
        }
        try {
            AtomicInteger next = new AtomicInteger();
            // The set check round-trips only a pack whose name is a pack id (#583, D2b).
            RoundTripping flow = root -> composition
                    .flow()
                    .run(
                            new PackIdentifier(root.name()),
                            Path.of(root.locator()),
                            run.resolve(next.incrementAndGet() + "-" + root.name()));
            return parsed.full()
                    ? check.checkFullWithRoundTrip(parsed.packs(), flow)
                    : check.checkWithRoundTrip(parsed.packs(), flow);
        } finally {
            delete(run);
        }
    }

    /** {@link #VALID} when standard output took everything written to it, else {@link #FAILED}, said on {@code err}. */
    private static int written(PrintStream out, PrintStream err) {
        out.flush();
        if (out.checkError()) {
            err.println("pack-doctor: cannot write to standard output");
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
