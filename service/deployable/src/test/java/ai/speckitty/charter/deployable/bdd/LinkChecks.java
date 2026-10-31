package ai.speckitty.charter.deployable.bdd;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Stub: the link checks are not implemented yet. */
final class LinkChecks {

    record Answer(
            String method,
            String path,
            @Nullable String query,
            int status,
            @Nullable String location,
            JsonNode body) {}

    LinkChecks(ContractConformance contract, Set<String> relations) {}

    List<String> violations(Answer answer) {
        return List.of();
    }
}
