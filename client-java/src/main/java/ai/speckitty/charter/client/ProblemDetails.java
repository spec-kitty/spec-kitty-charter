// SPDX-License-Identifier: MIT
package ai.speckitty.charter.client;

import ai.speckitty.charter.client.model.Problem;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Reads the error body of an {@link ApiException} as the contract's {@link Problem}.
 *
 * <p>The charter service answers every error as {@code application/problem+json} (RFC 9457). The generated client
 * reports it as an {@code ApiException} whose body is text; this turns that text into the generated model, so a
 * caller reads the status, the title and, for a refusal, the {@code code}. A member this client does not know is kept
 * in the model's additional properties, not refused.
 *
 * <p>The response's content type is not consulted: a proxy may relabel it, and the body decides. A body is a Problem
 * when it is a JSON object with a non-null {@code title} and {@code status}.
 *
 * <p>It is the one hand-written class of the client (operator ruling on #217).
 */
@NullMarked
public final class ProblemDetails {

    private ProblemDetails() {}

    /**
     * Reads the error's body as a Problem.
     *
     * @param error an error the generated client threw
     * @return the Problem the body holds; empty when there is no body, or the body is not a JSON object that is a
     *     Problem (a {@code title} and a {@code status}), for example a proxy's HTML page or a cut-off answer
     */
    public static Optional<Problem> from(ApiException error) {
        String body = error.getResponseBody();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            if (!JsonParser.parseString(body).isJsonObject()) {
                return Optional.empty();
            }
            Problem problem = Problem.fromJson(body);
            return problem.getTitle() != null && problem.getStatus() != null ? Optional.of(problem) : Optional.empty();
        } catch (JsonParseException | IllegalArgumentException | IOException notAProblem) {
            return Optional.empty();
        }
    }
}
