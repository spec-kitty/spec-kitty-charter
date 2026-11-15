package ai.speckitty.charter.profiles.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Who carries out an agent profile: its {@code performer}. A profile that declares none is silent; selection reads it as
 * {@link #AGENT}. Only a {@link #HUMAN} profile may take the reserved role {@value AgentProfile#OPERATOR_ROLE}.
 */
public enum Performer {
    /** An agent carries out the profile. */
    AGENT("agent"),
    /** A human carries out the profile. */
    HUMAN("human");

    private final String token;

    Performer(String token) {
        this.token = token;
    }

    /**
     * The spelling the pack file and the contract use.
     *
     * @return the token, {@code agent} or {@code human}
     */
    public String token() {
        return token;
    }

    /**
     * Looks a performer up by its token.
     *
     * @param token a token, for example {@code human}
     * @return the performer; empty when no performer has that token
     */
    public static Optional<Performer> fromToken(String token) {
        return Arrays.stream(values()).filter(p -> p.token.equals(token)).findFirst();
    }
}
