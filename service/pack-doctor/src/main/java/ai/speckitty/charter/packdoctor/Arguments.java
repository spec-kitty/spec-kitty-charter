package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packdoctor.report.ReportFile;
import ai.speckitty.charter.packdoctor.report.ReportFormat;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The command line of pack-doctor (#478, FR-001, T014; FR-007 of mission pack-doctor-coverage-01M7YMQK): a command
 * word first, {@code validate} or {@code remediate}; {@code --help} alone asks for the usage. A command line with no
 * command word, the options alone, is a usage fault: the old flat form is no alias (C-002).
 *
 * <p>{@code validate} takes {@code --config-root <dir>}, or one or more {@code --pack <dir>[:<level>][=<id>]}, then
 * optionally {@code --round-trip}, {@code --full}, and {@code --report <file>} with an optional
 * {@code --format yaml|xml} (FR-008 of mission pack-doctor-coverage-01M7YMQK, ruling R-i: YAML unless {@code --format}
 * names XML); {@code validate --help} asks for the usage too.
 * A pack is configured under its {@code =<id>} when one is given, its registered id (#583, D2b; PR #608 MEDIUM-3,
 * operator ruling B), and otherwise under its directory's name, as every pack of a config root is.
 *
 * <p>A user error is a value, never an exception: {@link #parse} answers {@link UsageFault} for no input, both inputs,
 * an unknown option, a missing value, an unknown level, a directory that does not exist, a config root with no pack, a
 * text after a {@code --pack} value's last {@code =} that is no pack id when the value names no directory, a
 * {@code --pack} value that could name two directories ({@code x:org} and {@code x}, or {@code x=id} and {@code x}, both
 * exist), two packs with one directory (by real path), one inside another or one id, and an entry of the config
 * root's {@code packs/} that is a link resolving to no directory (to nothing, or to a file), {@code --report} or
 * {@code --format} given twice, {@code --format} without {@code --report}, a format that is neither {@code yaml} nor
 * {@code xml}, a {@code --report} file inside a pack the run checks (by real path), and a {@code remediate} pack that is
 * or holds the working directory. A fault of a {@code remediate} value names {@code remediate}, which takes its values
 * bare, where a {@code validate} one names {@code --pack}. Whether the report file can be written is learnt by writing
 * it. A value that starts with {@code --} is a missing value, unless it follows {@code --}, which ends the options. It
 * judges the command line and the directories it names, never a pack's content: that is {@code pack-validation}'s.
 */
final class Arguments {

    /** The usage, as {@code --help} prints it and a usage fault ends with. */
    static final String USAGE = """
            usage: pack-doctor validate (--config-root <dir> | --pack <dir>[:<level>][=<id>] ...) [--round-trip] [--full]
                                        [--report <file> [--format yaml|xml]]
                   pack-doctor remediate <dir>[:<level>][=<id>] ...
              validate                 check a set of packs and write the report to standard output
              remediate                rewrite each pack in canonical form and restamp its manifest, print each
                                       pack's outcome, then validate the set with --round-trip
              --config-root <dir>      check every pack under <dir>/packs/, each at the level its pack.yaml declares
                                       and under its directory's name
              --pack <dir>[:<level>][=<id>]
                                       check the pack in <dir>, at <level> (built-in, org or project) when given,
                                       under the pack id <id> when given, else under the directory's name;
                                       repeat it to check a set of packs together
              --round-trip             also round-trip every pack through the charter's model
              --full                   also check, per pack, its substep delegations, its references and its
                                       step enhancements' targets, each against the packs it reaches over
                                       depends_on, as registration does
              --report <file>          also write the report to <file>, replacing it: the same report as on
                                       standard output, in YAML unless --format says otherwise
              --format yaml|xml        the report file's format, yaml by default; only with --report
              --help                   print this usage
              --                       end the options: each argument after it is a --pack value, for a
                                       directory whose name starts with -- (or write ./--dir)
            exit codes: 0 valid (warnings allowed), 1 a problem, 2 a usage or I/O failure
            locale: run under a UTF-8 locale (LC_ALL=C.UTF-8); under the POSIX locale a file name
                    that is not ASCII stops the run with an internal error (exit 2)
            """;

    private static final String CONFIG_ROOT = "--config-root";
    private static final String PACK = "--pack";
    private static final String ROUND_TRIP = "--round-trip";
    private static final String FULL = "--full";
    private static final String REPORT = "--report";
    private static final String FORMAT = "--format";
    private static final String HELP = "--help";
    private static final String END_OF_OPTIONS = "--";
    private static final String VALIDATE = "validate";
    private static final String REMEDIATE = "remediate";

    private Arguments() {}

    /** What a command line asks for: a check, the usage, a remediation, or nothing it can run. */
    sealed interface Outcome permits Parsed, Help, Remediate, UsageFault {}

    /**
     * A check to run.
     *
     * @param packs the packs, in the order given, or in name order under a config root; no two with one directory or
     *     one id
     * @param roundTrip whether the round trip is asked for
     * @param full whether {@code REFERENCES} is asked for ({@code --full}; #632 item 3)
     * @param inputs the config root or the {@code --pack} values, as written, which the report names
     * @param report where {@code --report} writes the report, and in which format; empty without {@code --report}
     */
    record Parsed(List<PackSetEntry> packs, boolean roundTrip, boolean full, Inputs inputs, Optional<ReportFile> report)
            implements Outcome {
        /**
         * Checks the fields and copies the list.
         *
         * @param packs the packs
         * @param roundTrip whether to round-trip
         * @param full whether to run {@code REFERENCES}
         * @param inputs what was given
         * @param report the report file, if any
         */
        Parsed {
            packs = List.copyOf(packs);
            Objects.requireNonNull(inputs, "inputs");
            Objects.requireNonNull(report, "report");
        }
    }

    /**
     * What a check was given, as written on the command line: the report's {@code inputs}.
     *
     * @param configRoot the {@code --config-root} value, when one was given
     * @param packs the {@code --pack} values, in order; empty under a config root
     */
    record Inputs(Optional<String> configRoot, List<String> packs) {
        /**
         * Checks the fields and copies the list.
         *
         * @param configRoot the config root, if any
         * @param packs the pack values
         */
        Inputs {
            Objects.requireNonNull(configRoot, "configRoot");
            packs = List.copyOf(packs);
        }
    }

    /** {@code --help}: print the usage, check nothing. */
    record Help() implements Outcome {}

    /**
     * {@code remediate} (FR-009 of mission pack-doctor-coverage-01M7YMQK, plan D7): the packs to remediate, each given
     * as a {@code --pack} value is, and the check of the same set with the round trip that follows.
     *
     * @param validation the packs, as a {@code validate --round-trip} of them
     */
    record Remediate(Parsed validation) implements Outcome {
        /**
         * Checks the field.
         *
         * @param validation the check that follows
         */
        Remediate {
            Objects.requireNonNull(validation, "validation");
        }
    }

    /**
     * A command line pack-doctor cannot run.
     *
     * @param message what is wrong, in one line
     */
    record UsageFault(String message) implements Outcome {
        /**
         * Checks the field.
         *
         * @param message the message
         */
        UsageFault {
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * Parses a command line: its command word, then that command's options.
     *
     * @param args the arguments, as {@code main} receives them
     * @return the check to run, the usage, {@code remediate}, or the fault
     */
    static Outcome parse(String[] args) {
        if (args.length == 1 && HELP.equals(args[0])) {
            return new Help();
        }
        if (args.length == 0) {
            return new UsageFault("no command: give " + VALIDATE + " or " + REMEDIATE);
        }
        String[] options = Arrays.copyOfRange(args, 1, args.length);
        return switch (args[0]) {
            case VALIDATE -> validate(options);
            case REMEDIATE -> remediate(options);
            default ->
                args[0].startsWith("-")
                        ? new UsageFault("no command: give " + VALIDATE + " or " + REMEDIATE + " before the options")
                        : new UsageFault("unknown command " + args[0]);
        };
    }

    /**
     * Parses the options of {@code validate}, the command word already taken.
     *
     * @param args the options after {@code validate}
     * @return the check to run, the usage, or the fault
     */
    static Outcome validate(String[] args) {
        if (args.length == 1 && HELP.equals(args[0])) {
            return new Help();
        }
        Collected collected = new Collected();
        Iterator<String> given = Arrays.asList(args).iterator();
        while (given.hasNext()) {
            Optional<UsageFault> fault = collected.take(given.next(), given);
            if (fault.isPresent()) {
                return fault.get();
            }
        }
        return collected.outcome();
    }

    /**
     * Parses the arguments of {@code remediate}, the command word already taken: one or more pack directories, each read
     * as a {@code --pack} value is, and no option.
     *
     * @param args the arguments after {@code remediate}
     * @return the remediation, the usage, or the fault
     */
    static Outcome remediate(String[] args) {
        if (args.length == 1 && HELP.equals(args[0])) {
            return new Help();
        }
        if (args.length == 0) {
            return new UsageFault("nothing to remediate: give one or more <dir>[:<level>][=<id>]");
        }
        Collected collected = new Collected();
        for (String arg : args) {
            if (arg.startsWith(END_OF_OPTIONS)) {
                return new UsageFault("unknown option " + arg);
            }
            collected.packs.add(arg);
        }
        collected.option = REMEDIATE;
        collected.roundTrip = true;
        Outcome outcome = collected.outcome();
        if (!(outcome instanceof Parsed parsed)) {
            return outcome;
        }
        Optional<UsageFault> holdsWorkingDirectory = holdingTheWorkingDirectory(parsed);
        return holdsWorkingDirectory.isPresent() ? holdsWorkingDirectory.get() : new Remediate(parsed);
    }

    /** What the options asked for so far. */
    private static final class Collected {
        /** How a fault names a pack value: {@code --pack}, or {@code remediate}, which takes its values bare (B9). */
        private String option = PACK;

        private Optional<String> configRoot = Optional.empty();
        private final List<String> packs = new ArrayList<>();
        private boolean roundTrip;
        private boolean full;
        private Optional<String> report = Optional.empty();
        private Optional<String> format = Optional.empty();

        /** Takes one argument, and its value from {@code rest}; the fault it is, if any. */
        Optional<UsageFault> take(String arg, Iterator<String> rest) {
            switch (arg) {
                case END_OF_OPTIONS -> rest.forEachRemaining(packs::add);
                case ROUND_TRIP -> roundTrip = true;
                case FULL -> full = true;
                case CONFIG_ROOT, PACK, REPORT, FORMAT -> {
                    return value(arg, rest);
                }
                default -> {
                    return Optional.of(new UsageFault("unknown option " + arg));
                }
            }
            return Optional.empty();
        }

        /**
         * The value of {@code --config-root}, {@code --pack}, {@code --report} or {@code --format}: a missing one is one
         * that starts with {@code --}.
         */
        private Optional<UsageFault> value(String option, Iterator<String> rest) {
            String value = rest.hasNext() ? rest.next() : END_OF_OPTIONS;
            if (value.startsWith(END_OF_OPTIONS)) {
                return Optional.of(new UsageFault(option + " needs a value"));
            }
            if (PACK.equals(option)) {
                packs.add(value);
                return Optional.empty();
            }
            if (REPORT.equals(option) || FORMAT.equals(option)) {
                return reportValue(option, value);
            }
            if (configRoot.isPresent()) {
                return Optional.of(new UsageFault(CONFIG_ROOT + " is given twice"));
            }
            configRoot = Optional.of(value);
            return Optional.empty();
        }

        /** The value of {@code --report} or {@code --format}, each given once. */
        private Optional<UsageFault> reportValue(String option, String value) {
            boolean isReport = REPORT.equals(option);
            if ((isReport ? report : format).isPresent()) {
                return Optional.of(new UsageFault(option + " is given twice"));
            }
            if (isReport) {
                report = Optional.of(value);
            } else {
                format = Optional.of(value);
            }
            return Optional.empty();
        }

        /** What the check was given, for the report. */
        Inputs inputs() {
            return new Inputs(configRoot, configRoot.isPresent() ? List.of() : packs);
        }

        /** The report file; checked by {@link #outcome} before any directory is. */
        Optional<ReportFile> reportFile() {
            return report.map(path -> new ReportFile(
                    Path.of(path), format.flatMap(ReportFormat::fromToken).orElse(ReportFormat.YAML)));
        }

        Outcome outcome() {
            if (format.isPresent() && report.isEmpty()) {
                return new UsageFault(FORMAT + " needs " + REPORT);
            }
            if (format.isPresent() && ReportFormat.fromToken(format.get()).isEmpty()) {
                return new UsageFault("unknown format " + format.get() + " for " + FORMAT + "; the formats are "
                        + ReportFormat.tokens());
            }
            return packsOutcome();
        }

        /** The packs the check is over, from the config root or the {@code --pack} values. */
        private Outcome packsOutcome() {
            if (configRoot.isPresent() && !packs.isEmpty()) {
                return new UsageFault("give " + CONFIG_ROOT + " or " + PACK + ", not both");
            }
            if (configRoot.isPresent()) {
                return configured(configRoot.get(), this);
            }
            if (packs.isEmpty()) {
                return new UsageFault(
                        "nothing to check: give " + CONFIG_ROOT + " <dir> or " + PACK + " <dir>[:<level>][=<id>]");
            }
            return given(packs, this);
        }
    }

    /** The packs of a config root: every directory under its {@code packs/}, at its declared level. */
    private static Outcome configured(String root, Collected asked) {
        Path directory = Path.of(root);
        if (!Files.isDirectory(directory)) {
            return new UsageFault(CONFIG_ROOT + " " + root + " is not a directory");
        }
        ConfigRoot.Listing listing;
        try {
            listing = ConfigRoot.packs(directory);
        } catch (IOException failure) {
            return new UsageFault("cannot list " + directory.resolve(ConfigRoot.PACKS) + ": " + failure.getMessage());
        }
        if (!listing.strayLinks().isEmpty()) {
            return new UsageFault(
                    listing.strayLinks().stream().map(Path::toString).collect(Collectors.joining(", "))
                            + (listing.strayLinks().size() == 1 ? " is a link" : " are links")
                            + " to no directory; a pack entry must resolve to a directory");
        }
        List<PackRoot> roots = listing.packs();
        if (roots.isEmpty()) {
            return new UsageFault(
                    "nothing to check: " + directory.resolve(ConfigRoot.PACKS) + " holds no pack directory");
        }
        return distinct(roots, asked);
    }

    /**
     * The pack of {@code remediate} that is or holds the working directory, refused (squad B6): the swap moves the pack's
     * directory aside and deletes it, so the working directory, and every relative path the check after it reads, would
     * name nothing.
     */
    private static Optional<UsageFault> holdingTheWorkingDirectory(Parsed parsed) {
        Path working;
        try {
            working = Path.of("").toRealPath();
        } catch (IOException failure) {
            return Optional.of(new UsageFault("cannot resolve the working directory: " + failure.getMessage()));
        }
        for (PackSetEntry entry : parsed.packs()) {
            String locator = entry.root().locator();
            try {
                if (working.startsWith(Path.of(locator).toRealPath())) {
                    return Optional.of(new UsageFault(REMEDIATE + " " + locator
                            + " holds the working directory, which the swap would move; run " + REMEDIATE
                            + " from outside the pack"));
                }
            } catch (IOException failure) {
                return Optional.of(new UsageFault("cannot resolve " + locator + ": " + failure.getMessage()));
            }
        }
        return Optional.empty();
    }

    /** The packs of repeated {@code --pack}, each at its level when one is given, under its id when one is given. */
    private static Outcome given(List<String> values, Collected asked) {
        List<PackRoot> roots = new ArrayList<>();
        for (String value : values) {
            Given given = packRoot(value, asked.option);
            if (given.fault().isPresent()) {
                return given.fault().get();
            }
            roots.add(given.root().orElseThrow());
        }
        return distinct(roots, asked);
    }

    /**
     * One {@code --pack} value: its root, or why it has none.
     *
     * @param named whether a reading of the value names an existing directory: true for a root, and for a value that is
     *     ambiguous because two readings do
     */
    private record Given(Optional<PackRoot> root, Optional<UsageFault> fault, boolean named) {

        static Given of(PackRoot root) {
            return new Given(Optional.of(root), Optional.empty(), true);
        }

        static Given refused(String message) {
            return new Given(Optional.empty(), Optional.of(new UsageFault(message)), false);
        }

        static Given ambiguous(String message) {
            return new Given(Optional.empty(), Optional.of(new UsageFault(message)), true);
        }

        /** This root under the registered id {@code id} (#583, D2b), whatever its directory is called. */
        Given under(String id) {
            return of(root.map(found -> new PackRoot(id, found.configuredLevel(), found.locator()))
                    .orElseThrow());
        }
    }

    /**
     * One {@code --pack <dir>[:<level>][=<id>]} (PR #608 MEDIUM-3, operator ruling B). The id is the text after the last
     * {@code =} when it is a pack id ({@link PackIdentifier#isPackId}); the level is then read from what remains, by
     * {@link #levelled}'s rule. The pack is configured under the id, whatever its directory is called; with no id, under
     * its directory's name.
     *
     * <p>A directory whose name holds an {@code =} still parses: when the value read with no id names a directory and
     * the value read with an id does not, the value is that directory, under its own name; when both name one, the
     * value is ambiguous. A text after the last {@code =} that is no pack id is a usage fault unless the value, read
     * with no id, names a directory: it is never taken into the path of a directory that does not exist.
     */
    private static Given packRoot(String value, String option) {
        int equals = value.lastIndexOf('=');
        if (equals < 0) {
            return levelled(value, option);
        }
        String id = value.substring(equals + 1);
        String rest = value.substring(0, equals);
        Given plain = levelled(value, option);
        if (!PackIdentifier.isPackId(id)) {
            if (plain.named()) {
                return plain;
            }
            return Given.refused(option + " " + value + ": "
                    + (id.isEmpty() ? "nothing follows the last =" : id + " after the last = is not a pack id")
                    + ", and " + value + " is not a directory; write " + option + " <dir>[:<level>]=<id>. "
                    + PackIdentifier.GRAMMAR_DETAIL);
        }
        if (rest.isEmpty() && !plain.named()) {
            return emptyDirectory(value, option);
        }
        Given withId = levelled(rest, option);
        if (withId.named() && plain.named()) {
            if (withId.root().isEmpty()) {
                // The id reading is itself ambiguous (x:org and x are both directories): no one spelling names it.
                return Given.ambiguous(option + " " + value + " is ambiguous: it names the directory " + value
                        + " under its own name, and " + rest + " is itself ambiguous; rename one of the directories");
            }
            PackRoot idReading = withId.root().orElseThrow();
            return Given.ambiguous(option + " " + value + " is ambiguous: it names the directory " + value
                    + " under its own name, and the directory " + idReading.locator() + " under the id " + id
                    + "; rename one, write " + unambiguous(idReading, id) + " for " + idReading.locator()
                    + " under the id " + id + ", or write " + value + "=<id> for " + value + " under an id");
        }
        if (withId.root().isPresent()) {
            return withId.under(id);
        }
        if (plain.named()) {
            return plain;
        }
        return withId;
    }

    /**
     * The {@code --pack} spelling of {@code root} under {@code id} that no directory name can capture: a separator ends
     * the directory part ({@code x/=y}, {@code x/:org=y}), so the whole value names a path inside {@code x}, never a
     * sibling directory whose name holds the {@code =}.
     */
    private static String unambiguous(PackRoot root, String id) {
        return root.locator() + "/"
                + root.configuredLevel().map(level -> ":" + level.token()).orElse("") + "=" + id;
    }

    /**
     * One {@code <dir>[:<level>]}, the pack under its directory's name. The level is the text after the last {@code :}
     * when it is a level token, so a path that holds a {@code :} ({@code C:\packs\x}) parses; a suffix that is a bare
     * word but no level, on a path that is not a directory, is an unknown level. When the whole value is a directory and
     * the text before the colon is not, the whole value is the directory, at no configured level; when both are, the
     * value is ambiguous.
     */
    private static Given levelled(String value, String option) {
        int colon = value.lastIndexOf(':');
        String suffix = colon < 0 ? "" : value.substring(colon + 1);
        Optional<PackLevel> level = PackLevel.fromToken(suffix);
        String path = level.isPresent() ? value.substring(0, colon) : value;
        if (level.isPresent() && Files.isDirectory(Path.of(value)) && Files.isDirectory(Path.of(path))) {
            return Given.ambiguous(option + " " + value + " is ambiguous: " + value + " and " + path
                    + " are both directories; rename one, or write " + value + ":<level>");
        }
        if (level.isEmpty() && isBareWord(suffix) && !Files.isDirectory(Path.of(value))) {
            return Given.refused("unknown level " + suffix + " in " + option + " " + value + "; the levels are "
                    + Arrays.stream(PackLevel.values()).map(PackLevel::token).collect(Collectors.joining(", ")));
        }
        if (level.isPresent() && Files.isDirectory(Path.of(value)) && !Files.isDirectory(Path.of(path))) {
            // x:org is a directory and x is not: the value names the directory x:org, at no configured level.
            return directoryOf(value, Optional.empty(), value, option);
        }
        // An empty path (:org, or an empty value) names the working directory and passes here; directoryOf refuses it.
        if (level.isPresent() && !Files.isDirectory(Path.of(path))) {
            // Neither x:org nor x is a directory: name the value as given, and the directory it was read as.
            return Given.refused(option + " " + value + " is not a directory, and nor is " + path);
        }
        return directoryOf(path, level, value, option);
    }

    /**
     * A {@code --pack} value whose directory part is empty ({@code =<id>}, {@code :<level>}): refused, never read as the
     * working directory, which the empty path names.
     */
    private static Given emptyDirectory(String value, String option) {
        return Given.refused(option + " " + value + " names no directory: its directory part is empty; write " + option
                + " <dir>[:<level>][=<id>]");
    }

    /**
     * The pack in {@code path}, at {@code level} when one is given; refused when {@code path} is empty or no pack
     * directory.
     *
     * @param path the directory part of the value
     * @param level the configured level, when one is given
     * @param given the {@code --pack} value {@code path} was read from, which a refusal of an empty path names
     * @param option how a refusal names the value: {@code --pack}, or {@code remediate}
     * @return the pack, or why there is none
     */
    private static Given directoryOf(String path, Optional<PackLevel> level, String given, String option) {
        if (path.isEmpty()) {
            return emptyDirectory(given, option);
        }
        Path directory = Path.of(path);
        if (!Files.isDirectory(directory)) {
            return Given.refused(option + " " + path + " is not a directory");
        }
        Path name = directory.toAbsolutePath().normalize().getFileName();
        if (name == null) {
            return Given.refused(option + " " + path + " names no pack directory");
        }
        return Given.of(new PackRoot(name.toString(), level, path));
    }

    private static boolean isBareWord(String suffix) {
        return !suffix.isEmpty() && suffix.indexOf('/') < 0 && suffix.indexOf('\\') < 0;
    }

    /**
     * The packs, refused when two share a directory (by real path), one lies inside another, or two share an id, given
     * or taken from the directory's name.
     */
    private static Outcome distinct(List<PackRoot> roots, Collected asked) {
        Map<Path, String> byRealPath = new LinkedHashMap<>();
        Map<String, String> byName = new HashMap<>();
        List<PackSetEntry> entries = new ArrayList<>();
        for (PackRoot root : roots) {
            Path real;
            try {
                real = Path.of(root.locator()).toRealPath();
            } catch (IOException failure) {
                return new UsageFault("cannot resolve " + root.locator() + ": " + failure.getMessage());
            }
            String sameDirectory = byRealPath.putIfAbsent(real, root.locator());
            if (sameDirectory != null) {
                return new UsageFault(
                        sameDirectory + " and " + root.locator() + " are the same directory; give each pack once");
            }
            for (Map.Entry<Path, String> other : byRealPath.entrySet()) {
                if (!other.getKey().equals(real)
                        && (real.startsWith(other.getKey()) || other.getKey().startsWith(real))) {
                    return new UsageFault(other.getValue() + " and " + root.locator()
                            + " overlap, one inside the other; a pack holds no other pack");
                }
            }
            String sameName = byName.putIfAbsent(root.name(), root.locator());
            if (sameName != null) {
                return new UsageFault(sameName + " and " + root.locator() + " are both under the id " + root.name()
                        + "; a set holds one pack of each id");
            }
            entries.add(new PackSetEntry(root));
        }
        Optional<ReportFile> report = asked.reportFile();
        if (report.isPresent()) {
            Optional<UsageFault> inside = reportInsideAPack(report.get().path(), byRealPath);
            if (inside.isPresent()) {
                return inside.get();
            }
        }
        return new Parsed(entries, asked.roundTrip, asked.full, asked.inputs(), report);
    }

    /**
     * The report file refused when it lies inside a pack the run checks, by real path (squad B7): writing it would change
     * the pack. A report path that is a link is judged where the link leads, link after link, so a link to a file not
     * written yet inside a pack is refused too (fold review LOW). A path whose directory does not exist is left for the
     * write to refuse.
     */
    private static Optional<UsageFault> reportInsideAPack(Path given, Map<Path, String> packsByRealPath) {
        Path real;
        try {
            Path report = linkTarget(given);
            Path parent = report.toAbsolutePath().getParent();
            Path name = report.getFileName();
            if (Files.exists(report)) {
                real = report.toRealPath();
            } else if (parent != null && name != null && Files.isDirectory(parent)) {
                real = parent.toRealPath().resolve(name);
            } else {
                return Optional.empty();
            }
        } catch (IOException failure) {
            return Optional.empty(); // the write says why the file cannot be written
        }
        for (Map.Entry<Path, String> pack : packsByRealPath.entrySet()) {
            if (real.startsWith(pack.getKey())) {
                return Optional.of(new UsageFault(REPORT + " " + given + " lies inside the pack " + pack.getValue()
                        + "; write the report outside every pack it checks"));
            }
        }
        return Optional.empty();
    }

    /** The most links a report path is followed through before the write is left to refuse it, as the kernel's bound. */
    private static final int MAX_LINKS = 40;

    /**
     * Where a path leads when it is a link, each link's text resolved against the link's own directory, whether or not
     * the last target exists; the path itself when it is no link.
     */
    private static Path linkTarget(Path path) throws IOException {
        Path at = path.toAbsolutePath();
        for (int hop = 0; hop < MAX_LINKS && Files.isSymbolicLink(at); hop++) {
            Path text = Files.readSymbolicLink(at);
            Path directory = at.getParent();
            at = directory == null ? text.toAbsolutePath() : directory.resolve(text);
        }
        return at;
    }
}
