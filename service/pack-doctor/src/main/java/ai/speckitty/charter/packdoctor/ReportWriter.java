package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packdoctor.Arguments.Parsed;
import ai.speckitty.charter.packdoctor.Problems.Problem;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Skip;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What pack-doctor writes (#478, FR-005, FR-006, T017; FR-008 of mission pack-doctor-coverage-01M7YMQK): the report, as
 * the contract's {@code PackDoctorReport} holds it, and one line per warning on standard error, never in the report.
 *
 * <p>The report is one tree ({@link #tree}, plan D6): standard output carries it as JSON ({@link #json}), and
 * {@code --report} writes the same tree as YAML or XML ({@code report.ReportFormat}). Its keys are in a fixed order
 * ({@code tool}, {@code inputs}, {@code checks}, {@code packs}, {@code skipped} when a check was skipped,
 * {@code problems}, {@code summary}), and a field with no value is left out, never written as {@code null}.
 */
final class ReportWriter {

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    /** The tool's name, the report's {@code tool.name}. */
    static final String TOOL = "pack-doctor";

    /** The resource beside this class that holds the build's version, the report's {@code tool.version}. */
    private static final String VERSION_RESOURCE = "pack-doctor.properties";

    private ReportWriter() {}

    /**
     * The report of a run: the tool, the inputs, the verdict's checks, packs and skipped checks, its problems, and the
     * summary of them.
     *
     * @param parsed the command line the run was given
     * @param verdict the pack-set verdict
     * @param problems its problems ({@link Problems#of})
     * @return the tree, in the report's key order
     */
    static ObjectNode tree(Parsed parsed, PackSetVerdict verdict, List<Problem> problems) {
        ObjectNode report = JSON.createObjectNode();
        report.putObject("tool").put("name", TOOL).put("version", version());
        ObjectNode inputs = report.putObject("inputs");
        parsed.inputs().configRoot().ifPresentOrElse(root -> inputs.put("config_root", root), () -> {
            ArrayNode packs = inputs.putArray("packs");
            parsed.inputs().packs().forEach(packs::add);
        });
        // --full (WP05, ruling R-u) is not an option of this command line yet: nothing asks for REFERENCES.
        inputs.putObject("options").put("full", false).put("round_trip", parsed.roundTrip());
        ArrayNode checks = report.putArray("checks");
        verdict.checks().forEach(check -> checks.add(check.name()));
        ArrayNode packs = report.putArray("packs");
        for (PackResult result : verdict.packs()) {
            ObjectNode pack = packs.addObject();
            pack.put("name", result.entry().name());
            result.level().ifPresent(level -> pack.put("level", level.token()));
            pack.put("path", result.entry().root().locator());
        }
        if (!verdict.skipped().isEmpty()) {
            ArrayNode skipped = report.putArray("skipped");
            for (Skip skip : verdict.skipped()) {
                skipped.addObject().put("check", skip.check().name()).put("reason", skip.reason());
            }
        }
        ArrayNode written = report.putArray("problems");
        problems.forEach(problem -> problem(written.addObject(), problem));
        report.putObject("summary")
                .put("packs", verdict.packs().size())
                .put("violations", Problems.violations(verdict))
                .put("warnings", warnings(verdict).size())
                .put(
                        "notices",
                        Math.toIntExact(findings(verdict, Severity.NOTICE).count()))
                .put("exit_code", problems.isEmpty() ? PackDoctorMain.VALID : PackDoctorMain.PROBLEMS);
        return report;
    }

    /**
     * The tree as standard output carries it: indented JSON ending with a line end.
     *
     * @param tree the report's tree
     * @return the JSON
     */
    static String json(JsonNode tree) {
        return JSON.writeValueAsString(tree) + System.lineSeparator();
    }

    /**
     * The build's version, from the resource the build writes beside this class.
     *
     * @return the version
     * @throws IllegalStateException when the resource is missing or names no version
     */
    static String version() {
        Properties properties = new Properties();
        try (InputStream resource = ReportWriter.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (resource == null) {
                throw new IllegalStateException(
                        VERSION_RESOURCE + " is missing beside " + ReportWriter.class.getName());
            }
            properties.load(resource);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read " + VERSION_RESOURCE, failure);
        }
        String version = properties.getProperty("version", "");
        if (version.isBlank()) {
            throw new IllegalStateException(VERSION_RESOURCE + " names no version");
        }
        return version;
    }

    /** One problem, in the order of the contract's {@code Problem} schema. */
    private static void problem(ObjectNode node, Problem problem) {
        node.put("title", problem.title());
        node.put("status", problem.status());
        node.put("detail", problem.detail());
        problem.instance().ifPresent(instance -> node.put("instance", instance));
        node.put("code", problem.code());
        if (!problem.violations().isEmpty()) {
            ArrayNode violations = node.putArray("violations");
            for (RefusalViolation violation : problem.violations()) {
                violations
                        .addObject()
                        .put("pointer", violation.pointer())
                        .put("code", violation.code())
                        .put("detail", violation.detail());
            }
        }
        if (!problem.packs().isEmpty()) {
            ArrayNode packs = node.putArray("packs");
            for (RefusalPack pack : problem.packs()) {
                packs.addObject()
                        .put("descriptorPackId", pack.descriptorPackId().value())
                        .put("packId", pack.packId().value())
                        .put("name", pack.name());
            }
        }
    }

    /**
     * The warning lines of a verdict, for standard error: one per warning of each pack's verdict, in the set's order,
     * as {@code warning: <pack>: <code> <file>: <detail>}.
     *
     * @param verdict the pack-set verdict
     * @return the lines, without line ends
     */
    static List<String> warnings(PackSetVerdict verdict) {
        return verdict.packs().stream()
                .flatMap(result -> result.verdict().findings().stream()
                        .filter(reason -> reason.isFailing(Severity.WARNING))
                        .map(reason -> warning(result.entry().name(), reason)))
                .toList();
    }

    /** The findings of each pack's verdict at one severity, in the set's order. */
    private static Stream<Reason> findings(PackSetVerdict verdict, Severity severity) {
        return verdict.packs().stream()
                .flatMap(result -> result.verdict().findings().stream())
                .filter(reason -> reason.isFailing(severity));
    }

    private static String warning(String pack, Reason reason) {
        return "warning: " + pack + ": " + reason.code().value() + " "
                + reason.location().file() + ": " + reason.detail();
    }
}
