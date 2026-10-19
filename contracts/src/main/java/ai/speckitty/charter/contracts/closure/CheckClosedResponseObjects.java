package ai.speckitty.charter.contracts.closure;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * The command line of the checker, run by the Gradle task {@code checkClosedResponseObjects}: one argument, the
 * bundle; exit 0 when it is closed, 1 with one line per violation when it is not, 2 when it cannot be read.
 */
public final class CheckClosedResponseObjects {

    private CheckClosedResponseObjects() {}

    public static void main(String[] args) {
        int code = run(List.of(args), System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(List<String> args, PrintStream out) {
        if (args.size() != 1) {
            out.println("usage: CheckClosedResponseObjects <bundled-openapi.yaml>");
            return 2;
        }
        ClosureReport report;
        try {
            report = ClosureChecker.check(Path.of(args.getFirst()));
        } catch (IOException | YamlEngineException e) {
            out.println("Cannot read " + args.getFirst() + ": " + e.getMessage());
            return 2;
        }
        if (report.passes()) {
            out.println("All " + report.positions() + " response schemas are closed.");
            return 0;
        }
        int count = report.violations().size();
        out.println(
                count + (count == 1 ? " violation" : " violations") + " of the closed-response-objects convention:");
        report.violations().forEach(out::println);
        return 1;
    }
}
