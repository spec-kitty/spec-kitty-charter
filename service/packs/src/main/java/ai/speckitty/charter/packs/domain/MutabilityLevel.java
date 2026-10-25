package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.packs.domain.WritePolicyGate.Operation;
import java.util.Arrays;
import java.util.Optional;

/**
 * The mutability level the owning pack declares for an artifact (ADR-0012): what a later pack may do with it. A level
 * is a set of allowed operations, so a finer flag can be added later without migrating declared levels.
 *
 * <p>ADR-0012 §Decision 1 states the levels over its three operations &mdash; <em>override</em> (replace or change an
 * existing artifact's fields), <em>enhance</em> (author an {@code enhances} relation) and <em>deactivate</em>:
 *
 * <pre>
 *   level       override   enhance   deactivate
 *   open        yes        yes       yes
 *   extendable  no         yes       no
 *   locked      no         no        no
 * </pre>
 *
 * <p>The registration API exposes three {@link Operation}s, mapped onto that table (FR-011, analysis finding U2):
 *
 * <ul>
 *   <li>{@link Operation#REGISTER} &mdash; add a brand-new artifact. An addition, not an override; refused only by a
 *       {@code locked} target, which forbids new content in its scope. Permitted by {@code open} and
 *       {@code extendable}.
 *   <li>{@link Operation#REPLACE} &mdash; replace an existing artifact's fields. Requires <em>override</em>, so only
 *       {@code open} permits it.
 *   <li>{@link Operation#RELATIONSHIP} &mdash; author an edge touching the artifact. Requires <em>enhance</em>, so
 *       {@code open} and {@code extendable} permit it; {@code locked} refuses it.
 * </ul>
 *
 * <p>Refusal matrix (a write beyond the owning pack's level is refused {@code governance_policy_violation}, 409):
 * {@code extendable}+{@code REPLACE}; {@code locked}+{@code REGISTER}; {@code locked}+{@code REPLACE};
 * {@code locked}+{@code RELATIONSHIP}. An artifact a policy does not name, in a pack with no policy file, is
 * {@code open} (ADR-0012 §Decision 2) &mdash; the default resolved by {@link MutabilityPolicySource}.
 */
public enum MutabilityLevel {
    /** Override, enhance and deactivate are all allowed. The default for any artifact no policy names. */
    OPEN("open"),
    /** Enhance is allowed; override and deactivate are refused. */
    EXTENDABLE("extendable"),
    /** No override, enhance or deactivate. ADR-0012 names this the level the drafts called {@code enforced}. */
    LOCKED("locked");

    private final String token;

    MutabilityLevel(String token) {
        this.token = token;
    }

    /**
     * The spelling a policy file uses.
     *
     * @return the token, for example {@code extendable}
     */
    public String token() {
        return token;
    }

    /**
     * Whether this level permits a registration operation, per the documented ADR-0012 mapping.
     *
     * @param operation the write about to be performed
     * @return {@code true} when the operation is within this level, {@code false} when it exceeds it
     */
    public boolean permits(Operation operation) {
        return switch (operation) {
            case REGISTER -> this != LOCKED;
            case REPLACE -> this == OPEN;
            case RELATIONSHIP -> this == OPEN || this == EXTENDABLE;
        };
    }

    /**
     * Looks a level up by its token.
     *
     * @param token a token, for example {@code locked}
     * @return the level; empty when no level has that token
     */
    public static Optional<MutabilityLevel> fromToken(String token) {
        return Arrays.stream(values())
                .filter(level -> level.token.equals(token))
                .findFirst();
    }
}
