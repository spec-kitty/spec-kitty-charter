package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.selection.ProfileSelection;
import ai.speckitty.charter.profiles.domain.selection.SelectionAnswer;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@code selectAgentProfile} (ruling D1 of slice G): which agent profile fills a role under an activation, and
 * {@code selectAgentProfileForStep} (FR-012): which fills the role one step names. The candidates, the pin and the
 * fallback in force come from {@link SelectionCandidates}, which refuses the activation, an unknown step and the
 * conflicts first; the selection is the domain's {@link ProfileSelection}, the same for both reads; this service refuses
 * a role nothing fills ({@code no_profile_matched}) and only then compares {@code If-None-Match}, so no refusal is a
 * {@code 304}.
 */
@Service
public final class ProfileSelectionService {

    private final SelectionCandidates source;

    /**
     * A service over the candidates port.
     *
     * @param source where the candidates of a role under an activation are read from
     */
    @Autowired
    public ProfileSelectionService(SelectionCandidates source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Selects the profile that fills a role under an activation.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param role the asked role, never {@code generic}: the REST edge refuses it by pattern
     * @param context the asked context
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the selection, or that the client holds the current representation
     * @throws CharterRefusal as the port refuses, then with {@code NO_PROFILE_MATCHED} when no profile in force fills the
     *     role and no fallback answers it
     */
    public Outcome select(String activationId, String role, SelectionContext context, @Nullable String ifNoneMatch) {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(context, "context");
        return answer(source.read(activationId, role, context), new Subject.Role(role), context, ifNoneMatch);
    }

    /**
     * Selects the profile of one step under an activation (FR-012): exactly what {@link #select} answers for the role the
     * step names. A step names a role and never a profile, and a substep never delegates to one, so the role is the only
     * source of the step's profile. A step that names no role is one any role may act on, so the fallback in force
     * answers it ({@link ProfileSelection#selectForAnyRole}).
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param missionType the step's mission type
     * @param action the step
     * @param context the asked context
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the selection, or that the client holds the current representation
     * @throws CharterRefusal as the port refuses, an unknown mission type or step included, then with
     *     {@code NO_PROFILE_MATCHED} as {@link #select} refuses, or for a step that names no role when no fallback is in
     *     force
     */
    public Outcome selectForStep(
            String activationId,
            String missionType,
            String action,
            SelectionContext context,
            @Nullable String ifNoneMatch) {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(missionType, "missionType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        SelectionCandidates.StepRead read = source.readForStep(activationId, missionType, action, context);
        return answer(read.read(), new Subject.Step(missionType, action, read.role()), context, ifNoneMatch);
    }

    /** The selection of the subject's role, or of any role, refused when nothing answers, then compared to the tag. */
    private static Outcome answer(
            SelectionCandidates.Read read, Subject subject, SelectionContext context, @Nullable String ifNoneMatch) {
        Optional<String> role = subject.role();
        SelectionAnswer answer = role.map(asked -> ProfileSelection.select(asked, context, read.candidates()))
                .orElseGet(() -> ProfileSelection.selectForAnyRole(read.candidates()));
        SelectionAnswer.Matched matched = switch (answer) {
            case SelectionAnswer.Matched found -> found;
            case SelectionAnswer.NoMatch _ ->
                throw new CharterRefusal(RefusalCode.NO_PROFILE_MATCHED, noMatch(read.activationId(), subject));
        };
        if (EntityTags.noneMatch(ifNoneMatch, read.etag())) {
            return new Outcome.NotModified(read.etag());
        }
        return new Outcome.Found(
                new Selection(read.activationId(), subject, context, matched), read.sourceFingerprint(), read.etag());
    }

    private static String noMatch(String activationId, Subject subject) {
        String under = "No agent profile in force under the activation " + activationId;
        return switch (subject) {
            case Subject.Role role -> under + " fills the role " + role.name() + ", and " + why(role.name());
            case Subject.Step step ->
                step.role()
                        .map(role -> under + " fills the role " + role + " of the step " + step.missionType() + "/"
                                + step.action() + ", and " + why(role))
                        .orElseGet(() -> under + " answers the step " + step.missionType() + "/" + step.action()
                                + ", which names no role, and no fallback profile is in force.");
        };
    }

    private static String why(String role) {
        return AgentProfile.OPERATOR_ROLE.equals(role)
                ? "the role operator never falls back."
                : "no fallback profile is in force.";
    }

    /** What a selection was asked for: a role, or a step and the role it names. */
    public sealed interface Subject {

        /**
         * The role selection reads.
         *
         * @return the asked role, or the role the step names; empty for a step that names none
         */
        Optional<String> role();

        /**
         * A role, asked by name ({@code selectAgentProfile}).
         *
         * @param name the asked role
         */
        record Role(String name) implements Subject {

            /**
             * Checks the role.
             *
             * @param name the role
             */
            public Role {
                Objects.requireNonNull(name, "name");
            }

            @Override
            public Optional<String> role() {
                return Optional.of(name);
            }
        }

        /**
         * A step, asked by its mission type and id ({@code selectAgentProfileForStep}), with the role it names.
         *
         * @param missionType the step's mission type
         * @param action the step
         * @param role the role the step names; empty when it names none
         */
        record Step(String missionType, String action, Optional<String> role) implements Subject {

            /**
             * Checks the fields.
             *
             * @param missionType the mission type
             * @param action the step
             * @param role the step's role
             */
            public Step {
                Objects.requireNonNull(missionType, "missionType");
                Objects.requireNonNull(action, "action");
                Objects.requireNonNull(role, "role");
            }
        }
    }

    /**
     * The answer of a selection.
     *
     * @param activationId the activation's id
     * @param subject what the selection was asked for
     * @param context the asked context
     * @param answer the picked profiles, and whether they tie
     */
    public record Selection(
            String activationId, Subject subject, SelectionContext context, SelectionAnswer.Matched answer) {

        /**
         * Checks the fields.
         *
         * @param activationId the activation's id
         * @param subject what was asked
         * @param context the context
         * @param answer the answer
         */
        public Selection {
            Objects.requireNonNull(activationId, "activationId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(answer, "answer");
        }
    }

    /** What a selection answers. */
    public sealed interface Outcome {

        /**
         * The client holds the current representation ({@code 304}).
         *
         * @param etag the current entity tag, unquoted
         */
        record NotModified(String etag) implements Outcome {

            /**
             * Checks the tag.
             *
             * @param etag the tag
             */
            public NotModified {
                Objects.requireNonNull(etag, "etag");
            }
        }

        /**
         * The current representation.
         *
         * @param selection the selection
         * @param sourceFingerprint the source fingerprint the read verified
         * @param etag its entity tag, unquoted
         */
        record Found(Selection selection, String sourceFingerprint, String etag) implements Outcome {

            /**
             * Checks the fields.
             *
             * @param selection the selection
             * @param sourceFingerprint the source fingerprint
             * @param etag the tag
             */
            public Found {
                Objects.requireNonNull(selection, "selection");
                Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
                Objects.requireNonNull(etag, "etag");
            }
        }
    }
}
