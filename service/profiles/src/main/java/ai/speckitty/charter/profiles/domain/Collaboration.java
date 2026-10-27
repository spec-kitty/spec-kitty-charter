package ai.speckitty.charter.profiles.domain;

import java.util.List;

/**
 * How an {@link AgentProfile} works with others: its {@code collaboration}. Every list keeps its authored order and
 * duplicates; a profile that declares none of them has {@link #NONE}.
 *
 * @param handoffTo the roles it hands work to ({@code handoff-to})
 * @param handoffFrom the roles it takes work from ({@code handoff-from})
 * @param worksWith the roles it works beside ({@code works-with})
 * @param outputArtifacts what it produces ({@code output-artifacts})
 * @param operatingProcedures the procedures it follows ({@code operating-procedures})
 * @param canonicalVerbs the verbs that name its work ({@code canonical-verbs})
 */
public record Collaboration(
        List<String> handoffTo,
        List<String> handoffFrom,
        List<String> worksWith,
        List<String> outputArtifacts,
        List<String> operatingProcedures,
        List<String> canonicalVerbs) {

    /** No collaboration declared. */
    public static final Collaboration NONE =
            new Collaboration(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    /**
     * Copies the lists.
     *
     * @param handoffTo the handoff-to roles
     * @param handoffFrom the handoff-from roles
     * @param worksWith the works-with roles
     * @param outputArtifacts the output artifacts
     * @param operatingProcedures the operating procedures
     * @param canonicalVerbs the canonical verbs
     */
    public Collaboration {
        handoffTo = List.copyOf(handoffTo);
        handoffFrom = List.copyOf(handoffFrom);
        worksWith = List.copyOf(worksWith);
        outputArtifacts = List.copyOf(outputArtifacts);
        operatingProcedures = List.copyOf(operatingProcedures);
        canonicalVerbs = List.copyOf(canonicalVerbs);
    }
}
