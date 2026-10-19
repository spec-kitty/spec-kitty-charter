package ai.speckitty.charter.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A loaded Charter Pack: its configured name and level, its descriptor when it has one, its graph and
 * the load findings that did not stop the load.
 *
 * @param name the name the pack is configured under
 * @param level the level the pack is configured at
 * @param descriptor the pack's {@code pack.yaml}; empty when the pack has none
 * @param graph the pack's own graph, before any merge
 * @param diagnostics the load findings that did not stop the load; empty when there are none
 */
public record CharterPack(
        String name,
        PackLevel level,
        Optional<PackDescriptor> descriptor,
        DoctrineGraph graph,
        List<Diagnostic> diagnostics) {
    /**
     * Checks the pack and copies its diagnostics.
     *
     * @param name the configured name
     * @param level the configured level
     * @param descriptor the pack's {@code pack.yaml}, when it has one
     * @param graph the pack's graph
     * @param diagnostics the load findings
     * @throws CharterRefusal with {@link RefusalCode#PACK_LEVEL_MISMATCH} when the descriptor declares a level other
     *     than {@code level}
     */
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
