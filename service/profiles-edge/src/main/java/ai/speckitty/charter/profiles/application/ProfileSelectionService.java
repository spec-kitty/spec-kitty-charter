package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.selection.ProfileSelection;
import ai.speckitty.charter.profiles.domain.selection.SelectionAnswer;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@code selectAgentProfile} (ruling D1 of slice G): which agent profile fills a role under an activation. The
 * candidates, the pin and the fallback in force come from {@link SelectionCandidates}, which refuses the activation and
 * the conflicts first; the selection is the domain's {@link ProfileSelection}; this service refuses a role nothing fills
 * ({@code no_profile_matched}) and only then compares {@code If-None-Match}, so no refusal is a {@code 304}.
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
        SelectionCandidates.Read read = source.read(activationId, role, context);
        SelectionAnswer.Matched matched = switch (ProfileSelection.select(role, context, read.candidates())) {
            case SelectionAnswer.Matched found -> found;
            case SelectionAnswer.NoMatch _ ->
                throw new CharterRefusal(
                        RefusalCode.NO_PROFILE_MATCHED,
                        "No agent profile in force under the activation " + read.activationId() + " fills the role "
                                + role + ", and "
                                + (AgentProfile.OPERATOR_ROLE.equals(role)
                                        ? "the role operator never falls back."
                                        : "no fallback profile is in force."));
        };
        if (EntityTags.noneMatch(ifNoneMatch, read.etag())) {
            return new Outcome.NotModified(read.etag());
        }
        return new Outcome.Found(
                new Selection(read.activationId(), role, context, matched), read.sourceFingerprint(), read.etag());
    }

    /**
     * The answer of a selection.
     *
     * @param activationId the activation's id
     * @param role the asked role
     * @param context the asked context
     * @param answer the picked profiles, and whether they tie
     */
    public record Selection(
            String activationId, String role, SelectionContext context, SelectionAnswer.Matched answer) {

        /**
         * Checks the fields.
         *
         * @param activationId the activation's id
         * @param role the role
         * @param context the context
         * @param answer the answer
         */
        public Selection {
            Objects.requireNonNull(activationId, "activationId");
            Objects.requireNonNull(role, "role");
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
