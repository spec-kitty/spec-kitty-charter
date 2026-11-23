package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packdoctor.Problems.Problem;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Skip;
import java.util.List;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What pack-doctor writes (#478, FR-005, FR-006, T017): the report on standard output, as the contract's
 * {@code PackDoctorReport} holds it, and one line per warning on standard error, never in the report.
 *
 * <p>The report's keys are in a fixed order ({@code checks}, {@code packs}, {@code skipped} when a check was skipped,
 * {@code problems}), and a field with no value is left out, never written as {@code null}.
 */
final class ReportWriter {

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private ReportWriter() {}

    /**
     * The report of a verdict and its problems, as JSON text ending with a newline.
     *
     * @param verdict the pack-set verdict
     * @param problems its problems ({@link Problems#of})
     * @return the report
     */
    static String report(PackSetVerdict verdict, List<Problem> problems) {
        ObjectNode report = JSON.createObjectNode();
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
        return JSON.writeValueAsString(report) + System.lineSeparator();
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

    private static String warning(String pack, Reason reason) {
        return "warning: " + pack + ": " + reason.code().value() + " "
                + reason.location().file() + ": " + reason.detail();
    }
}
