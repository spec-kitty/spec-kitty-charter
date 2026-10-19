package ai.speckitty.charter.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A loaded Charter Pack: its configured name and level, its descriptor when it has one, its graph and
 * the load findings that did not stop the load.
 */
public record CharterPack(
        String name,
        PackLevel level,
        Optional<PackDescriptor> descriptor,
        DoctrineGraph graph,
        List<Diagnostic> diagnostics) {
    public CharterPack {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(graph, "graph");
        diagnostics = List.copyOf(diagnostics);
        descriptor.ifPresent(d -> {
            if (d.level() != level) {
                throw new CharterRefusal(
                        RefusalCode.PACK_LEVEL_MISMATCH,
                        "Pack '" + name + "' is configured as " + level.token() + " but declares pack_level "
                                + d.level().token());
            }
        });
    }
}
