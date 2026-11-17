package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two subset checks of #554 (SC-001, NFR-002, T053), over every planted and example pack, on the recorded answers:
 * the registration's ({@code golden/surfaces.json}, which {@code SurfaceGoldenTest} holds to the live answer) and
 * validate mode's ({@code :pack-validation}'s {@code golden/validate.json}, which its {@code ValidateGoldenTest} holds to
 * the live verdict).
 *
 * <ul>
 *   <li><b>Forward:</b> every fault the registration answers, validate mode answers too, with the same code, file and
 *       wording; so does every file it does not hold. It holds by construction (S1, S6: both judge the one read's
 *       rationale, validate mode with the placement rules after it); this pins it.
 *   <li><b>Reverse:</b> the registration answers what it answered before #554 apart from NFR-002's diff list, which
 *       WP12 adds to only with S2 (rows 14, 15 and 38: the format prechecks and a repeated asset id) and S8 (rows 22 to
 *       24: a pack whose {@code pack.yaml} is missing or unreadable answers every fault). The rows each touches are
 *       named here, so a registration row that changes for any other reason fails.
 * </ul>
 */
@DisplayName("the registration's faults are a subset of validate mode's, and change only where NFR-002 lists (#554)")
class SurfaceSubsetTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final JsonNode SURFACES = classpath("/golden/surfaces.json");

    private static final JsonNode VALIDATE = validateGolden();

    /** The format prechecks S2 adds to the registration. */
    private static final Set<String> S2_CODES =
            Set.of("asset_mime_invalid", "requires_core_invalid", "pack_version_invalid", "dependency_range_invalid");

    /** The rows where S2 adds a fault to the registration (R2 rows 14, 15 and 38). */
    private static final Set<String> S2_ROWS = Set.of(
            "planted/asset-mime-does-not-match-the-extension",
            "planted/asset-mime-does-not-match-the-extension+pack.yaml",
            "planted/asset-mime-malformed",
            "planted/asset-mime-malformed+pack.yaml",
            "planted/asset-repeated-id-only-the-first-owner-is-checked",
            "planted/asset-repeated-id-only-the-first-owner-is-checked+pack.yaml",
            "planted/asset-repeated-id-the-first-owner-escapes",
            "planted/asset-repeated-id-the-first-owner-escapes+pack.yaml",
            "planted/requires-core-invalid");

    /** The three plants whose {@code pack.yaml} is there but unreadable (R2 rows 22 and 23), the other S8 rows. */
    private static final Set<String> UNREADABLE_DESCRIPTORS = Set.of(
            "planted/c9-pack-version-missing-at-the-org-level",
            "planted/control-retired-pack-key-is-a-warning",
            "planted/control-unknown-pack-key-is-a-warning",
            "planted/pack-yaml-tilde-is-null");

    private static JsonNode classpath(String resource) {
        try (InputStream in = Objects.requireNonNull(SurfaceSubsetTest.class.getResourceAsStream(resource), resource)) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Validate mode's golden, in the sibling module that owns it; the test runs in this module's directory. */
    private static JsonNode validateGolden() {
        Path file = Path.of("..", "pack-validation", "src", "test", "resources", "golden", "validate.json");
        try {
            return JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One answer of a surface: a code at a file, in its words. */
    private record Fault(String file, String code, String detail) {}

    private static List<Fault> registration(String key) {
        JsonNode registration = SURFACES.get(key).get("registration");
        List<Fault> faults = new ArrayList<>();
        JsonNode refused = registration.get("refused");
        if (refused != null) {
            refused.get("violations")
                    .forEach(row -> faults.add(new Fault(
                            row.get("pointer").asString(),
                            row.get("code").asString(),
                            row.get("detail").asString())));
        }
        return faults;
    }

    private static List<String> notHeld(String key) {
        JsonNode accepted = SURFACES.get(key).get("registration").get("accepted");
        List<String> details = new ArrayList<>();
        if (accepted != null) {
            accepted.get("diagnostics").forEach(row -> {
                if ("file_not_held".equals(row.get("code").asString())) {
                    details.add(row.get("detail").asString());
                }
            });
        }
        return details;
    }

    private static List<Fault> validate(String key, String severity) {
        List<Fault> faults = new ArrayList<>();
        VALIDATE.get(key).get("findings").forEach(row -> {
            if (severity.equals(row.get("severity").asString())) {
                faults.add(new Fault(
                        row.get("file").asString(),
                        row.get("code").asString(),
                        row.get("detail").asString()));
            }
        });
        return faults;
    }

    static List<Subject> subjects() {
        return GoldenSubjects.subjects();
    }

    @Test
    @DisplayName("both goldens hold the same subjects")
    void bothGoldensHoldTheSameSubjects() {
        assertThat(new ArrayList<>(SURFACES.propertyNames()))
                .containsExactlyInAnyOrderElementsOf(VALIDATE.propertyNames());
        assertThat(subjects()).extracting(Subject::key).containsExactlyInAnyOrderElementsOf(SURFACES.propertyNames());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("forward (SC-001): every registration fault and file not held is validate mode's, word for word")
    void theRegistrationsFaultsAreValidateModes(Subject subject) {
        assertThat(validate(subject.key(), "violation"))
                .as("%s: validate mode names every fault the registration refuses", subject.key())
                .containsAll(registration(subject.key()));
        assertThat(validate(subject.key(), "warning").stream()
                        .filter(fault -> "file_not_held".equals(fault.code()))
                        .map(Fault::detail)
                        .toList())
                .as("%s: validate mode warns of every file the registration does not hold", subject.key())
                .containsAll(notHeld(subject.key()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("reverse (NFR-002): a registration row gains a fault only where S2 or S8 lists it")
    void theRegistrationChangesOnlyOnTheListedRows(Subject subject) {
        String key = subject.key();
        List<Fault> faults = registration(key);
        boolean s2 = faults.stream().anyMatch(SurfaceSubsetTest::isS2);
        assertThat(s2).as("%s gains an S2 fault exactly when S2 lists it", key).isEqualTo(S2_ROWS.contains(key));
        // A plant without pack.yaml is the one the golden judges again with a descriptor, as its +pack.yaml twin.
        boolean bare = SURFACES.has(key + GoldenSubjects.WITH_DESCRIPTOR);
        boolean s8 = bare || UNREADABLE_DESCRIPTORS.contains(key);
        boolean descriptor = faults.stream()
                .anyMatch(fault -> "pack.yaml".equals(fault.file()) && "pack_unreadable".equals(fault.code()));
        assertThat(descriptor)
                .as("%s answers the descriptor's refusal, among every fault, exactly on an S8 row", key)
                .isEqualTo(s8);
    }

    @Test
    @DisplayName("S8 and S2 together touch the 43 registration rows NFR-002 lists for WP12")
    void theListedRowsAreTheFortyThree() {
        long s8 = subjects().stream()
                .map(Subject::key)
                .filter(key -> registration(key).stream()
                        .anyMatch(fault -> "pack.yaml".equals(fault.file()) && "pack_unreadable".equals(fault.code())))
                .count();
        long s2Only = S2_ROWS.stream()
                .filter(key -> registration(key).stream()
                        .noneMatch(fault -> "pack.yaml".equals(fault.file()) && "pack_unreadable".equals(fault.code())))
                .count();

        assertThat(s8).as("the S8 rows (R2 rows 22 to 24)").isEqualTo(38);
        assertThat(s8 + s2Only).isEqualTo(43);
    }

    private static boolean isS2(Fault fault) {
        return S2_CODES.contains(fault.code())
                || ("duplicate_artifact_id".equals(fault.code()) && fault.file().endsWith(".asset.yaml"));
    }
}
