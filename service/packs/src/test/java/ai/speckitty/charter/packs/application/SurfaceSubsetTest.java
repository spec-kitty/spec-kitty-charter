package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import tools.jackson.databind.node.ObjectNode;

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
 *       named here, so a registration row that changes for any other reason fails. The landing's fixture fixes
 *       (the R2 note's "Landing fixes") are named here too: W1's fixed payload path, and R1's root twins, which the
 *       baseline predates.
 * </ul>
 */
@DisplayName("the registration's faults are a subset of validate mode's, and change only where NFR-002 lists (#554)")
class SurfaceSubsetTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final JsonNode SURFACES = classpath("/golden/surfaces.json");

    private static final JsonNode VALIDATE = validateGolden();

    /**
     * The registration's answers before WP12 changed them, frozen from {@code surfaces.json} at the lane's base
     * ({@code 59f14afcc}): the baseline of the reverse subset.
     */
    private static final JsonNode BEFORE = classpath("/golden/registration-before-wp12.json");

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

    /**
     * The root twins of three open-case-6 plants, added at the landing (R1, the R2 note's "Landing fixes"): new subjects
     * the frozen baseline predates. The bare ones are S8 rows.
     */
    private static final Set<String> ROOT_TWINS = Set.of(
            "planted/c3-root-shard-edge-bare-endpoint",
            "planted/c3-root-shard-edge-bare-endpoint+pack.yaml",
            "planted/c7-root-shard-relation-extends",
            "planted/c7-root-shard-relation-extends+pack.yaml",
            "planted/r15-glossary-urn-in-a-root-shard",
            "planted/r15-glossary-urn-in-a-root-shard+pack.yaml");

    /**
     * Example packs added after the frozen baseline (#358: org-alt, the sibling whose contest the project pack settles;
     * ADR-0080 and the operator ruling of 2026-11-18 on #108: org-mandate, whose preset requires its routing catalog; #602: tensions, one case of each tension).
     * The baseline predates them, so the registration has nothing to answer as before: it must refuse nothing.
     */
    private static final Set<String> NEWER_EXAMPLES =
            Set.of("example-packs/org-alt", "example-packs/org-mandate", "example-packs/tensions");

    /**
     * Plants added after the frozen baseline for D1 of #478 (#597): the baseline predates them, and the registration
     * answers the link alone.
     */
    private static final Set<String> NEWER_LINK_PLANTS = Set.of("planted/pack-entry-linked-file");

    /**
     * The row of the plant whose sidecar's payload path the landing fixed (W1, a fixed fixture defect) that names the
     * payload before the landing: the plant with a descriptor. The bare plant answered its descriptor's refusal alone.
     */
    private static final String FIXED_PAYLOAD_ROW =
            "planted/asset-path-inside-through-a-symlink-is-contained" + GoldenSubjects.WITH_DESCRIPTOR;

    /** W1's fix: the sidecar's payload path before the landing, and after it. */
    private static final String PAYLOAD_BEFORE = "assets/in/x.txt";

    private static final String PAYLOAD_AFTER = "assets/in/keep.txt";

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

    /** One answer of a surface: a code at a file, or at a place in it ({@code <file>#<json-pointer>}), in its words. */
    private record Fault(String pointer, String code, String detail) {

        /** The file the fault is at, without the place in it. */
        String file() {
            return fileOf(pointer);
        }
    }

    /** The file of a wire pointer: what stands before its {@code #}. */
    private static String fileOf(String pointer) {
        int place = pointer.indexOf('#');
        return place < 0 ? pointer : pointer.substring(0, place);
    }

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
                        atPlace(row.get("file").asString(), row.get("place")),
                        row.get("code").asString(),
                        row.get("detail").asString()));
            }
        });
        return faults;
    }

    /** Validate mode's file and place, as the wire names them: the file, then {@code #} and the JSON pointer. */
    private static String atPlace(String file, JsonNode place) {
        StringBuilder pointer = new StringBuilder();
        place.forEach(segment ->
                pointer.append('/').append(segment.asString().replace("~", "~0").replace("/", "~1")));
        return pointer.isEmpty() ? file : file + "#" + pointer;
    }

    /**
     * A registration answer as the baseline, which predates the place inside a file, names it: each violation at its
     * file alone, and the detail naming the first at its file. The one change the in-file pointer makes to every row.
     */
    private static JsonNode atFiles(JsonNode registration) {
        JsonNode copy = registration.deepCopy();
        if (copy.get("refused") instanceof ObjectNode refused) {
            refused.get("violations")
                    .forEach(row -> ((ObjectNode) row)
                            .put("pointer", fileOf(row.get("pointer").asString())));
            refused.put("detail", refused.get("detail").asString().replaceAll("#/\\S*\\.$", "."));
        }
        return copy;
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
        // D1 (#597) adds one pack_entry_linked per link to the registration of every plant that plants a link.
        boolean d1 = faults.stream().anyMatch(SurfaceSubsetTest::isD1);
        assertThat(d1)
                .as("%s gains a pack_entry_linked exactly when it plants a link (D1)", key)
                .isEqualTo(subject.plantsLinks());
        if (NEWER_LINK_PLANTS.contains(key)) {
            assertThat(BEFORE.has(key)).as("%s is newer than the baseline", key).isFalse();
            assertThat(faults).as("%s answers its link alone", key).allMatch(SurfaceSubsetTest::isD1);
            return;
        }
        // A plant without pack.yaml is the one the golden judges again with a descriptor, as its +pack.yaml twin.
        boolean bare = SURFACES.has(key + GoldenSubjects.WITH_DESCRIPTOR);
        boolean s8 = bare || UNREADABLE_DESCRIPTORS.contains(key);
        boolean descriptor = faults.stream()
                .anyMatch(fault -> "pack.yaml".equals(fault.file()) && "pack_unreadable".equals(fault.code()));
        assertThat(descriptor)
                .as("%s answers the descriptor's refusal, among every fault, exactly on an S8 row", key)
                .isEqualTo(s8);
        if (NEWER_EXAMPLES.contains(key)) {
            assertThat(BEFORE.has(key)).as("%s is newer than the baseline", key).isFalse();
            assertThat(faults).as("%s is a canonical example pack", key).isEmpty();
            return;
        }
        if (ROOT_TWINS.contains(key)) {
            assertThat(BEFORE.has(key))
                    .as("%s is a landing twin, which the baseline predates", key)
                    .isFalse();
            return;
        }
        JsonNode before = FIXED_PAYLOAD_ROW.equals(key) ? fixedPayload(BEFORE.get(key)) : BEFORE.get(key);
        List<Fault> was = faults(before);
        JsonNode now = atFiles(SURFACES.get(key).get("registration"));
        List<Fault> atFiles = faults(now);
        if (s8) {
            assertThat(was)
                    .as("%s: before WP12 the registration answered the descriptor's refusal alone", key)
                    .hasSize(1)
                    .allSatisfy(fault -> assertThat(fault.file()).isEqualTo("pack.yaml"));
            assertThat(atFiles)
                    .as("%s: that refusal is one of every fault now (S8)", key)
                    .containsAll(was);
        } else if (s2) {
            assertThat(atFiles.stream()
                            .filter(fault -> !isS2(fault) && !isD1(fault))
                            .toList())
                    .as("%s: apart from S2's gains, the registration answers what it answered before WP12", key)
                    .isEqualTo(was);
        } else if (d1) {
            assertThat(atFiles.stream().filter(fault -> !isD1(fault)).toList())
                    .as("%s: apart from D1's links, the registration answers what it answered before WP12", key)
                    .isEqualTo(was);
        } else {
            assertThat(now)
                    .as("%s: outside NFR-002's list, the registration answers what it answered before WP12", key)
                    .isEqualTo(before);
        }
    }

    /**
     * The baseline answer of W1's plant with its payload path fixed as the landing fixed the fixture: the one change
     * its rows may carry (the R2 note's "Landing fixes").
     */
    private static JsonNode fixedPayload(JsonNode before) {
        String text = JSON.writeValueAsString(before);
        assertThat(text)
                .as("the baseline names the payload path the landing fixed")
                .contains(PAYLOAD_BEFORE);
        return JSON.readTree(text.replace(PAYLOAD_BEFORE, PAYLOAD_AFTER));
    }

    private static List<Fault> faults(JsonNode registration) {
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

    @Test
    @DisplayName(
            "S8 and S2 together touch the 43 registration rows NFR-002 lists for WP12, and the landing's three bare"
                    + " root twins")
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

        long bareTwins = ROOT_TWINS.stream()
                .filter(key -> !key.endsWith(GoldenSubjects.WITH_DESCRIPTOR))
                .count();

        assertThat(bareTwins).isEqualTo(3);
        assertThat(s8)
                .as("the S8 rows (R2 rows 22 to 24): WP12's 38 and the landing's bare twins")
                .isEqualTo(38 + bareTwins);
        assertThat(s8 - bareTwins + s2Only).isEqualTo(43);
    }

    @Test
    @DisplayName("one violation per descriptor finding: two unknown keys of pack.yaml are two violations, each one"
            + " validate mode answers (WP04 review MINOR-3)")
    void eachDescriptorFindingIsOneViolation(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        Path descriptor = pack.resolve("pack.yaml");
        Files.writeString(descriptor, Files.readString(descriptor) + "mystery: 1\nenigma: 2\n");
        PackReadout read =
                new FileSystemPackInspector().read(new PackRoot("org-base", Optional.empty(), pack.toString()));

        assertThatThrownBy(() -> PackRegistrationService.readUpload(
                        new FileSystemPackInspector(),
                        new WholePackStore(new YamlSerializationStrategy()),
                        "org-base",
                        new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.violations())
                            .extracting(RefusalViolation::pointer, RefusalViolation::code)
                            .containsExactly(
                                    tuple("pack.yaml", "pack_unreadable"), tuple("pack.yaml", "pack_unreadable"));
                    assertThat(refusal.violations())
                            .extracting(RefusalViolation::detail)
                            .containsExactlyElementsOf(read.rationale().violations().stream()
                                    .map(reason -> reason.detail())
                                    .toList());
                });
    }

    /** A link refused under D1 of #478 (#597), which the baseline predates. */
    private static boolean isD1(Fault fault) {
        return "pack_entry_linked".equals(fault.code());
    }

    private static boolean isS2(Fault fault) {
        return S2_CODES.contains(fault.code())
                || ("duplicate_artifact_id".equals(fault.code()) && fault.file().endsWith(".asset.yaml"));
    }
}
