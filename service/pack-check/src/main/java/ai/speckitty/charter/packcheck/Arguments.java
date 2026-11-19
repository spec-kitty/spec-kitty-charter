package ai.speckitty.charter.packcheck;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The command line of pack-check (#478, FR-001, T014): {@code --config-root <dir>}, or one or more
 * {@code --pack <dir>[:<level>]}, then optionally {@code --round-trip}; {@code --help} alone asks for the usage.
 *
 * <p>A user error is a value, never an exception: {@link #parse} answers {@link UsageFault} for no input, both inputs,
 * an unknown option, a missing value, an unknown level, a directory that does not exist, a config root with no pack, and
 * two packs with one directory (by real path) or one name. It judges the command line and the directories it names,
 * never a pack's content: that is {@code pack-validation}'s.
 */
final class Arguments {

    /** The usage, as {@code --help} prints it and a usage fault ends with. */
    static final String USAGE = """
            usage: pack-check (--config-root <dir> | --pack <dir>[:<level>] ...) [--round-trip]
              --config-root <dir>      check every pack under <dir>/packs/, each at the level its pack.yaml declares
              --pack <dir>[:<level>]   check the pack in <dir>, at <level> (built-in, org or project) when given;
                                       repeat it to check a set of packs together
              --round-trip             also round-trip every pack through the charter's model
              --help                   print this usage
            exit codes: 0 valid (warnings allowed), 1 a problem, 2 a usage or I/O failure
            """;

    private static final String CONFIG_ROOT = "--config-root";
    private static final String PACK = "--pack";
    private static final String ROUND_TRIP = "--round-trip";
    private static final String HELP = "--help";

    private Arguments() {}

    /** What a command line asks for: a check, the usage, or nothing it can run. */
    sealed interface Outcome permits Parsed, Help, UsageFault {}

    /**
     * A check to run.
     *
     * @param packs the packs, in the order given, or in name order under a config root; no two with one directory or
     *     one name
     * @param roundTrip whether the round trip is asked for
     */
    record Parsed(List<PackSetEntry> packs, boolean roundTrip) implements Outcome {
        /**
         * Checks the fields and copies the list.
         *
         * @param packs the packs
         * @param roundTrip whether to round-trip
         */
        Parsed {
            packs = List.copyOf(packs);
        }
    }

    /** {@code --help}: print the usage, check nothing. */
    record Help() implements Outcome {}

    /**
     * A command line pack-check cannot run.
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
     * Parses a command line.
     *
     * @param args the arguments, as {@code main} receives them
     * @return the check to run, the usage, or the fault
     */
    static Outcome parse(String[] args) {
        if (args.length == 1 && HELP.equals(args[0])) {
            return new Help();
        }
        Optional<String> configRoot = Optional.empty();
        List<String> packs = new ArrayList<>();
        boolean roundTrip = false;
        Iterator<String> given = Arrays.asList(args).iterator();
        while (given.hasNext()) {
            String arg = given.next();
            switch (arg) {
                case CONFIG_ROOT, PACK -> {
                    String value = given.hasNext() ? given.next() : "--";
                    if (value.startsWith("--")) {
                        return new UsageFault(arg + " needs a value");
                    }
                    if (CONFIG_ROOT.equals(arg)) {
                        if (configRoot.isPresent()) {
                            return new UsageFault(CONFIG_ROOT + " is given twice");
                        }
                        configRoot = Optional.of(value);
                    } else {
                        packs.add(value);
                    }
                }
                case ROUND_TRIP -> roundTrip = true;
                default -> {
                    return new UsageFault("unknown option " + arg);
                }
            }
        }
        if (configRoot.isPresent() && !packs.isEmpty()) {
            return new UsageFault("give " + CONFIG_ROOT + " or " + PACK + ", not both");
        }
        if (configRoot.isPresent()) {
            return configured(configRoot.get(), roundTrip);
        }
        if (packs.isEmpty()) {
            return new UsageFault("nothing to check: give " + CONFIG_ROOT + " <dir> or " + PACK + " <dir>[:<level>]");
        }
        return given(packs, roundTrip);
    }

    /** The packs of a config root: every directory under its {@code packs/}, at its declared level. */
    private static Outcome configured(String root, boolean roundTrip) {
        Path directory = Path.of(root);
        if (!Files.isDirectory(directory)) {
            return new UsageFault(CONFIG_ROOT + " " + root + " is not a directory");
        }
        List<PackRoot> roots;
        try {
            roots = ConfigRoot.packs(directory);
        } catch (IOException failure) {
            return new UsageFault("cannot list " + directory.resolve(ConfigRoot.PACKS) + ": " + failure.getMessage());
        }
        if (roots.isEmpty()) {
            return new UsageFault(
                    "nothing to check: " + directory.resolve(ConfigRoot.PACKS) + " holds no pack directory");
        }
        return distinct(roots, roundTrip);
    }

    /** The packs of repeated {@code --pack}, each at its level when one is given. */
    private static Outcome given(List<String> values, boolean roundTrip) {
        List<PackRoot> roots = new ArrayList<>();
        for (String value : values) {
            Given given = packRoot(value);
            if (given.fault().isPresent()) {
                return given.fault().get();
            }
            roots.add(given.root().orElseThrow());
        }
        return distinct(roots, roundTrip);
    }

    /** One {@code --pack} value: its root, or why it has none. */
    private record Given(Optional<PackRoot> root, Optional<UsageFault> fault) {

        static Given of(PackRoot root) {
            return new Given(Optional.of(root), Optional.empty());
        }

        static Given refused(String message) {
            return new Given(Optional.empty(), Optional.of(new UsageFault(message)));
        }
    }

    /**
     * One {@code --pack <dir>[:<level>]}. The level is the text after the last {@code :} when it is a level token, so a
     * path that holds a {@code :} ({@code C:\packs\x}) parses; a suffix that is a bare word but no level, on a path that
     * is not a directory, is an unknown level.
     */
    private static Given packRoot(String value) {
        int colon = value.lastIndexOf(':');
        String path = value;
        Optional<PackLevel> level = Optional.empty();
        if (colon >= 0) {
            String suffix = value.substring(colon + 1);
            Optional<PackLevel> token = PackLevel.fromToken(suffix);
            if (token.isPresent()) {
                path = value.substring(0, colon);
                level = token;
            } else if (isBareWord(suffix) && !Files.isDirectory(Path.of(value))) {
                return Given.refused("unknown level " + suffix + " in " + PACK + " " + value + "; the levels are "
                        + Arrays.stream(PackLevel.values())
                                .map(PackLevel::token)
                                .collect(Collectors.joining(", ")));
            }
        }
        Path directory = Path.of(path);
        if (!Files.isDirectory(directory)) {
            return Given.refused(PACK + " " + path + " is not a directory");
        }
        Path name = directory.toAbsolutePath().normalize().getFileName();
        if (name == null) {
            return Given.refused(PACK + " " + path + " names no pack directory");
        }
        return Given.of(new PackRoot(name.toString(), level, path));
    }

    private static boolean isBareWord(String suffix) {
        return !suffix.isEmpty() && suffix.indexOf('/') < 0 && suffix.indexOf('\\') < 0;
    }

    /** The packs, refused when two share a directory (by real path) or a name. */
    private static Outcome distinct(List<PackRoot> roots, boolean roundTrip) {
        Map<Path, String> byRealPath = new HashMap<>();
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
            String sameName = byName.putIfAbsent(root.name(), root.locator());
            if (sameName != null) {
                return new UsageFault(sameName + " and " + root.locator() + " are both named " + root.name()
                        + "; a set holds one pack of each name");
            }
            entries.add(new PackSetEntry(root));
        }
        return new Parsed(entries, roundTrip);
    }
}
