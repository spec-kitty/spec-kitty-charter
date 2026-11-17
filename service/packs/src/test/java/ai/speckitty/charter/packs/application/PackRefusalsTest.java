package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.golden.SurfaceAnswers;
import ai.speckitty.charter.packs.golden.SurfaceAnswers.Subject;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API edge's mapping of the one read's verdict (#554, FR-008, S6, T040, T041): the refusal of an upload and the
 * diagnostics of an accepted one, in the order the verdict gives, naming no storage code; and, over every golden pack,
 * what it answers for the verdict the domain port returns against what the registration answered when the golden was
 * taken ({@code golden/surfaces.json}), apart from the listed rows.
 */
@DisplayName("PackRefusals: the registration's answer from the verdict of the one read (#554, FR-008)")
class PackRefusalsTest {

    private static final ReasonCode SCHEMA = new ReasonCode("artifact_schema_error", Severity.VIOLATION);

    private static final ReasonCode NOT_HELD = new ReasonCode("file_not_held", Severity.WARNING);

    private static Reason failing(ReasonCode code, String file, String detail) {
        return new Reason(code.value(), Evaluation.FAIL, code, Location.in(file), detail);
    }

    @Test
    @DisplayName(
            "a failing verdict is validation_failed: one violation per violation, in order, and the problem detail")
    void aFailingVerdictIsTheRefusal() {
        Rationale verdict = new Rationale(List.of(
                failing(SCHEMA, "tactics/b.tactic.yaml", "tactics/b.tactic.yaml: 'id' is missing"),
                Reason.passed("checked"),
                failing(NOT_HELD, "notes.txt", "notes.txt: not held (no pack file kind models this file)"),
                failing(PackCodes.DUPLICATE_ARTIFACT_ID, "tactics/a.tactic.yaml", "already declared")));

        CharterRefusal refusal = PackRefusals.of(verdict);

        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refusal.getMessage())
                .isEqualTo("The pack cannot be registered: 2 fault(s), the first at tactics/b.tactic.yaml.");
        assertThat(refusal.violations())
                .containsExactly(
                        new RefusalViolation(
                                "tactics/b.tactic.yaml",
                                "artifact_schema_error",
                                "tactics/b.tactic.yaml: 'id' is missing"),
                        new RefusalViolation(
                                "tactics/a.tactic.yaml",
                                "duplicate_artifact_id",
                                "tactics/a.tactic.yaml: already declared"));
    }

    @Test
    @DisplayName(
            "a passing verdict refuses nothing; its warnings are the diagnostics, in order, by the token they spell")
    void aPassingVerdictsWarningsAreTheDiagnostics() {
        Rationale verdict = new Rationale(List.of(
                failing(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING, "p.prompt.md", "p.prompt.md: slot x holds text"),
                failing(NOT_HELD, "notes.txt", "not held (no pack file kind models this file)"),
                Reason.passed("checked")));

        assertThatThrownBy(() -> PackRefusals.of(verdict)).isInstanceOf(IllegalArgumentException.class);
        assertThat(PackRefusals.diagnostics(verdict))
                .containsExactly(
                        new Diagnostic(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY, "p.prompt.md: slot x holds text"),
                        new Diagnostic(
                                Diagnostic.Code.FILE_NOT_HELD,
                                "notes.txt: not held (no pack file kind models this file)"));
    }

    @Test
    @DisplayName("a warning no diagnostic code spells is a programming error, not a silent drop")
    void anUnspelledWarningIsAnError() {
        Rationale verdict =
                new Rationale(List.of(failing(new ReasonCode("mystery", Severity.WARNING), "a.yaml", "a.yaml: what")));

        assertThatThrownBy(() -> PackRefusals.diagnostics(verdict))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mystery");
    }

    // ---- golden equivalence (T041) --------------------------------------------------------------------------------

    /** What a registration answers, as the golden records it: the refusal's detail and violations, or diagnostics. */
    private record Answer(Optional<String> refusal, List<RefusalViolation> violations, List<Diagnostic> diagnostics) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final JsonNode GOLDEN = golden();

    /**
     * The rows where the store refused the pack as a whole on its descriptor (R2 rows 22 to 24): today's registration
     * answers that one violation, with the problem detail {@code The pack cannot be registered: <fault>}; the verdict is
     * collect-all, so it names the same fault after the graph faults and before the pack's other faults, under the
     * problem detail of every refusal ({@code N fault(s), the first at <file>.}). A decision for the orchestrator
     * before WP12.
     */
    private static final Set<String> DESCRIPTOR_ROWS = Set.of(
            "artifact-not-utf8",
            "artifact-not-yaml",
            "artifact-utf16-with-a-byte-order-mark",
            "asset-mime-does-not-match-the-extension",
            "asset-mime-malformed",
            "asset-path-escape-absolute",
            "asset-path-escape-dot-dot",
            "asset-path-escape-through-a-symlink",
            "asset-path-escape-windows-drive",
            "asset-path-inside-through-a-symlink-is-contained",
            "asset-path-symlink-loop",
            "asset-repeated-id-only-the-first-owner-is-checked",
            "asset-repeated-id-the-first-owner-escapes",
            "c10-agent-quoted-integer",
            "c10-agent-role-uppercase",
            "c10-agent-scalar-role",
            "c10-tactic-schema-version-unquoted",
            "c11-agent-routing-priority-101",
            "c11-directive-lenient-adherence-without-allowances",
            "c16-shard-edge-provenance",
            "c2-agent-nested-unknown-key",
            "c2-charter-nested-unknown-key",
            "c3-root-shard-urn-malformed",
            "c3-shard-edge-bare-endpoint",
            "c3-shard-urn-malformed",
            "c4-shard-urn-prefix-differs-from-kind",
            "c7-shard-relation-extends",
            "c8-shard-without-generated-by-is-accepted",
            "c9-pack-version-missing-at-the-org-level",
            "charter-cli-timeout-not-a-number",
            "charter-invalid",
            "charter-template-set",
            "charter-valid",
            "control-retired-pack-key-is-a-warning",
            "control-unknown-pack-key-is-a-warning",
            "duplicate-artifact-id",
            "pack-yaml-tilde-is-null",
            "r15-glossary-urn-in-a-shard");

    /** The rows S2 changes (R2 rows 14, 15 and 38): registration gains the repeated asset id and the format prechecks. */
    private static final Set<String> S2_ROWS = Set.of(
            "asset-mime-does-not-match-the-extension",
            "asset-mime-does-not-match-the-extension+pack.yaml",
            "asset-mime-malformed",
            "asset-mime-malformed+pack.yaml",
            "asset-repeated-id-only-the-first-owner-is-checked",
            "asset-repeated-id-only-the-first-owner-is-checked+pack.yaml",
            "asset-repeated-id-the-first-owner-escapes",
            "asset-repeated-id-the-first-owner-escapes+pack.yaml",
            "requires-core-invalid");

    private static final Set<String> S2_CODES =
            Set.of("asset_mime_invalid", "requires_core_invalid", "pack_version_invalid", "dependency_range_invalid");

    private static JsonNode golden() {
        try (InputStream in = Objects.requireNonNull(
                PackRefusalsTest.class.getResourceAsStream("/golden/surfaces.json"), "/golden/surfaces.json")) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Answer recorded(String key) {
        JsonNode registration = GOLDEN.get(key).get("registration");
        JsonNode refused = registration.get("refused");
        if (refused == null) {
            List<Diagnostic> diagnostics = new ArrayList<>();
            registration
                    .get("accepted")
                    .get("diagnostics")
                    .forEach(row -> diagnostics.add(new Diagnostic(
                            Diagnostic.Code.valueOf(row.get("code").asString().toUpperCase(java.util.Locale.ROOT)),
                            row.get("detail").asString())));
            return new Answer(Optional.empty(), List.of(), diagnostics);
        }
        assertThat(refused.get("code").asString()).isEqualTo(RefusalCode.VALIDATION_FAILED.token());
        List<RefusalViolation> violations = new ArrayList<>();
        refused.get("violations")
                .forEach(row -> violations.add(new RefusalViolation(
                        row.get("pointer").asString(),
                        row.get("code").asString(),
                        row.get("detail").asString())));
        return new Answer(Optional.of(refused.get("detail").asString()), violations, List.of());
    }

    private static Answer answered(Rationale verdict) {
        if (verdict.isFailing()) {
            CharterRefusal refusal = PackRefusals.of(verdict);
            return new Answer(Optional.of(String.valueOf(refusal.getMessage())), refusal.violations(), List.of());
        }
        return new Answer(Optional.empty(), List.of(), PackRefusals.diagnostics(verdict));
    }

    static List<Subject> subjects() {
        return SurfaceAnswers.subjects();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName(
            "over every golden pack, the verdict's refusal is the registration's recorded one, apart from the listed rows")
    void theVerdictAnswersAsTheRegistrationRecorded(Subject subject, @TempDir Path host) throws IOException {
        assumeLinks(subject, host);
        Path pack = SurfaceAnswers.copy(subject, Files.createDirectories(host.resolve("registration")));
        // As the registration reads a staged tree: under its directory's name, with no configured level.
        Rationale verdict = new FileSystemPackInspector()
                .read(new PackRoot(subject.name(), Optional.empty(), pack.toString()))
                .rationale();
        String row = subject.key().substring(subject.key().indexOf('/') + 1);
        Answer recorded = recorded(subject.key());
        Answer answered = answered(verdict);

        if (DESCRIPTOR_ROWS.contains(row)) {
            assertThat(recorded.violations())
                    .as("the store's one refusal at the descriptor")
                    .hasSize(1);
            assertThat(answered.violations()).contains(recorded.violations().getFirst());
            assertThat(answered.violations())
                    .as("the graph faults, then the refusal of the pack as a whole, lead")
                    .filteredOn(violation -> !"drg_schema_error".equals(violation.code()))
                    .first()
                    .isEqualTo(recorded.violations().getFirst());
            assertThat(answered.refusal())
                    .contains("The pack cannot be registered: "
                            + answered.violations().size() + " fault(s), the first at "
                            + answered.violations().getFirst().pointer() + ".");
            return;
        }
        if (S2_ROWS.contains(row)) {
            List<RefusalViolation> gained = answered.violations().stream()
                    .filter(PackRefusalsTest::isS2)
                    .toList();
            assertThat(gained).as("S2's gains").isNotEmpty();
            assertThat(answered.violations().stream().filter(v -> !isS2(v)).toList())
                    .isEqualTo(recorded.violations());
            assertThat(answered.diagnostics()).isEmpty();
            return;
        }
        assertThat(answered).isEqualTo(recorded);
    }

    /** A violation S2 adds to the registration: a format precheck, or a repeated id at an asset sidecar. */
    private static boolean isS2(RefusalViolation violation) {
        return S2_CODES.contains(violation.code())
                || (PackCodes.DUPLICATE_ARTIFACT_ID.value().equals(violation.code())
                        && violation.pointer().endsWith(".asset.yaml"));
    }

    /** A plant that needs symbolic links is skipped where the file system cannot make one, unless CI requires them. */
    private static void assumeLinks(Subject subject, Path host) throws IOException {
        if (!subject.plantsLinks()) {
            return;
        }
        Path target = Files.createTempFile(host, "probe", ".tmp");
        Path link = host.resolve("probe.lnk");
        boolean links;
        try {
            Files.createSymbolicLink(link, target);
            links = true;
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            links = false;
        } finally {
            Files.deleteIfExists(link);
            Files.delete(target);
        }
        assumeTrue(links || Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
    }
}
