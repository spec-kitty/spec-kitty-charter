package ai.speckitty.charter.packs.golden;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What the registration and the compose read answer for one pack, as one stable, ordered value (#554, IC-01, NFR-002):
 * the characterization the work packages after WP01 are compared with, over {@link GoldenSubjects}. Two surfaces:
 *
 * <ul>
 *   <li>{@code registration}: the staged load {@link WholePackStore#loadStaged}, as {@code PackRegistrationService} and
 *       {@code PackSourceStagers} call it (with the store production builds, {@code new
 *       WholePackStore(strategy)}); on a refusal its code, detail and violations (pointer, code, detail) in order, on
 *       success the diagnostics (code, detail) the registration answers, in order;
 *   <li>{@code composeRead}: the compose's read of the pack as a stored pack, {@link
 *       DomainRepositoryService#compositionFacts}; the refusal's code and detail ({@code pack_unreadable}, the first
 *       violation as {@code readable} words it), or {@code readable}.
 * </ul>
 *
 * <p>Validate mode's half moved with its subject to {@code :pack-validation}'s {@code ValidateGoldenTest} and
 * {@code golden/validate.json} (#554, WP09, Pr5).
 *
 * <p>Each surface reads its own fresh copy of the pack, so none sees what another did. The only normalisation is the
 * temporary directory the copies live in, which becomes {@value #SCRATCH} wherever a text names it: nothing else of a
 * detail is touched.
 */
public final class SurfaceAnswers {

    /** What the temporary directory the copies live in becomes in a recorded text. */
    static final String SCRATCH = "<scratch>";

    /** A recorded compose read that refused nothing. */
    static final String READABLE = "readable";

    private SurfaceAnswers() {}

    /**
     * What every surface answers for the subject.
     *
     * @param subject the pack
     * @param scratch an empty directory the copies are made in
     * @return {@code registration} and {@code composeRead}, in that order
     * @throws IOException when a copy or a link cannot be made
     */
    static Map<String, Object> of(Subject subject, Path scratch) throws IOException {
        Normaliser normaliser = new Normaliser(scratch);
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put(
                "registration",
                registration(GoldenSubjects.copy(subject, scratch.resolve("registration")), normaliser));
        answers.put(
                "composeRead",
                composeRead(subject, GoldenSubjects.copy(subject, scratch.resolve("compose-read")), normaliser));
        return answers;
    }

    private static Object registration(Path pack, Normaliser normaliser) {
        try {
            WholePackWriteStore.Contents contents =
                    new WholePackStore(new YamlSerializationStrategy()).loadStaged(new StagedPack(pack));
            List<Map<String, Object>> diagnostics = new ArrayList<>();
            contents.warnings().stream().map(PackFinding::toDiagnostic).forEach(diagnostic -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", diagnostic.code().token());
                row.put("detail", normaliser.apply(diagnostic.detail()));
                diagnostics.add(row);
            });
            return Map.of("accepted", Map.of("diagnostics", diagnostics));
        } catch (CharterRefusal refusal) {
            Map<String, Object> refused = refused(refusal, normaliser);
            List<Map<String, Object>> violations = new ArrayList<>();
            for (RefusalViolation violation : refusal.violations()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("pointer", normaliser.apply(violation.pointer()));
                row.put("code", violation.code());
                row.put("detail", normaliser.apply(violation.detail()));
                violations.add(row);
            }
            refused.put("violations", violations);
            return Map.of("refused", refused);
        } catch (RuntimeException thrown) {
            return threw(thrown, normaliser);
        }
    }

    private static Object composeRead(Subject subject, Path pack, Normaliser normaliser) {
        PackLocation location = new PackLocation(subject.name(), subject.storedLevel(), pack);
        try {
            new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy())
                    .compositionFacts(new PackIdentifier(subject.name()));
            return READABLE;
        } catch (CharterRefusal refusal) {
            return Map.of("refused", refused(refusal, normaliser));
        } catch (RuntimeException thrown) {
            return threw(thrown, normaliser);
        }
    }

    private static Map<String, Object> refused(CharterRefusal refusal, Normaliser normaliser) {
        Map<String, Object> refused = new LinkedHashMap<>();
        refused.put("code", refusal.code().token());
        refused.put("detail", normaliser.apply(String.valueOf(refusal.getMessage())));
        return refused;
    }

    /** A surface that threw something other than a refusal: recorded as it is, so a change of it shows. */
    private static Map<String, Object> threw(RuntimeException thrown, Normaliser normaliser) {
        Map<String, Object> threw = new LinkedHashMap<>();
        threw.put("type", thrown.getClass().getName());
        threw.put("message", normaliser.apply(String.valueOf(thrown.getMessage())));
        return Map.of("threw", threw);
    }

    /** Replaces the scratch directory, as written and as its real path, with {@value #SCRATCH}. */
    private static final class Normaliser implements Function<String, String> {

        private final List<String> spellings = new ArrayList<>();

        Normaliser(Path scratch) {
            try {
                spellings.add(scratch.toRealPath().toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            spellings.add(scratch.toAbsolutePath().normalize().toString());
        }

        @Override
        public String apply(String text) {
            String normalised = text;
            for (String spelling : spellings) {
                normalised = normalised.replace(spelling, SCRATCH);
            }
            return normalised;
        }
    }
}
