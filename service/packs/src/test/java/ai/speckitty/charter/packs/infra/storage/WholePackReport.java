package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.Evaluation;
import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.FileResult;
import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.Status;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * The whole-pack gate's report, as Markdown: totals, a table per gate kind, the problems, then every file. It carries
 * no timestamp, so two runs over the same pinned pack give identical bytes.
 */
final class WholePackReport {

    private WholePackReport() {}

    /**
     * Renders a judged run.
     *
     * @param evaluation the judged run
     * @param pack the pack's label, for example {@code spec-kitty-open-packs packs/built-in}
     * @param pin the pinned commit
     * @param information extra lines per file (for example duplicate graph edges), appended after its row
     * @return the report
     */
    static String render(Evaluation evaluation, String pack, String pin, Map<String, List<String>> information) {
        StringBuilder out = new StringBuilder();
        out.append("# Whole-pack round-trip report\n\n");
        out.append("- Pack: ").append(pack).append('\n');
        out.append("- Pin: `").append(pin).append("`\n");
        out.append("- Files: ").append(evaluation.files().size());
        for (Status status : Status.values()) {
            out.append(", ").append(label(status)).append(' ').append(count(evaluation, status));
        }
        out.append("\n\n## By kind\n\n").append(kindTable(evaluation));
        out.append("\n## Modelled kinds with no file\n\n");
        if (evaluation.emptyKinds().isEmpty()) {
            out.append("None: every modelled kind has a file in the pack.\n");
        } else {
            evaluation
                    .emptyKinds()
                    .forEach(entry -> out.append("- ")
                            .append(entry.kind())
                            .append(": not proved here. ")
                            .append(entry.reason())
                            .append(" (")
                            .append(entry.issue())
                            .append(")\n"));
        }
        out.append("\n## Problems\n\n");
        if (evaluation.problems().isEmpty()) {
            out.append("None: the observed result matches the expectation.\n");
        } else {
            evaluation
                    .problems()
                    .forEach(problem -> out.append("- ").append(problem).append('\n'));
        }
        out.append("\n## Files\n\n| path | kind | status | detail |\n| --- | --- | --- | --- |\n");
        for (FileResult file : evaluation.files()) {
            out.append("| ")
                    .append(cell(file.path()))
                    .append(" | ")
                    .append(cell(file.kind()))
                    .append(" | ")
                    .append(label(file.status()))
                    .append(" | ")
                    .append(cell(file.detail()))
                    .append(" |\n");
            for (String line : information.getOrDefault(file.path(), List.of())) {
                out.append("| ")
                        .append(cell(file.path()))
                        .append(" | | info | ")
                        .append(cell(line))
                        .append(" |\n");
            }
        }
        return out.toString();
    }

    /**
     * The per-kind table alone, for the console.
     *
     * @param evaluation the judged run
     * @return the Markdown table
     */
    static String kindTable(Evaluation evaluation) {
        StringBuilder out = new StringBuilder("| kind | files | green | expected red | excluded | red |\n");
        out.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (Map.Entry<String, SortedMap<Status, Integer>> kind :
                evaluation.byKind().entrySet()) {
            SortedMap<Status, Integer> counts = kind.getValue();
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            out.append("| ")
                    .append(cell(kind.getKey()))
                    .append(" | ")
                    .append(total)
                    .append(" | ")
                    .append(counts.getOrDefault(Status.GREEN, 0))
                    .append(" | ")
                    .append(counts.getOrDefault(Status.EXPECTED_RED, 0))
                    .append(" | ")
                    .append(counts.getOrDefault(Status.EXCLUDED, 0))
                    .append(" | ")
                    .append(counts.getOrDefault(Status.RED, 0))
                    .append(" |\n");
        }
        return out.toString();
    }

    private static long count(Evaluation evaluation, Status status) {
        return evaluation.files().stream()
                .filter(file -> file.status() == status)
                .count();
    }

    private static String label(Status status) {
        return switch (status) {
            case GREEN -> "green";
            case EXPECTED_RED -> "expected red";
            case EXCLUDED -> "excluded";
            case RED -> "red";
        };
    }

    private static String cell(String text) {
        return text.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ");
    }
}
