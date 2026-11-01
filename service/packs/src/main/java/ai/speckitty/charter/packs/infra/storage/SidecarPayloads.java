package ai.speckitty.charter.packs.infra.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The store's rule for an asset sidecar's payload (ADR-0040 decision 6), the one the load and validate mode both apply
 * so they refuse the same sidecars in the same words (#251). A sidecar's {@code path} must name, exactly, an asset file
 * the pack holds: one the listing classifies as an {@code asset-payload}, under {@code assets/}. A path that is missing,
 * spelled in another case, outside {@code assets/} or classified by another rule names none. Two sidecars may not name
 * one payload: the first, in {@link String} order of their files, owns it.
 */
final class SidecarPayloads {

    private SidecarPayloads() {}

    /**
     * A schema-valid sidecar, as the rule reads it.
     *
     * @param file the sidecar's pack-relative path
     * @param id its {@code id}
     * @param path its {@code path}, relative to {@code assets/}, as written
     */
    record Sidecar(String file, String id, String path) {}

    /**
     * A sidecar the rule refuses, and why.
     *
     * @param file the sidecar's pack-relative path
     * @param reason {@code payload assets/<path> does not exist} or {@code payload assets/<path> is already named by
     *     asset <id>}
     */
    record Refused(String file, String reason) {}

    /**
     * The sidecars the rule refuses, in the order given.
     *
     * @param sidecars the schema-valid sidecars, in {@link String} order of their files
     * @param held the asset files the pack holds, relative to {@code assets/}
     * @return the refused sidecars; every other one is kept
     */
    static List<Refused> refused(List<Sidecar> sidecars, Collection<String> held) {
        Set<String> payloads = Set.copyOf(held);
        Map<String, String> namedBy = new HashMap<>();
        List<Refused> refused = new ArrayList<>();
        for (Sidecar sidecar : sidecars) {
            String payload = PackFileSet.ASSETS + sidecar.path();
            String claimant = namedBy.get(sidecar.path());
            if (!payloads.contains(sidecar.path())) {
                refused.add(new Refused(sidecar.file(), "payload " + payload + " does not exist"));
            } else if (claimant != null) {
                refused.add(
                        new Refused(sidecar.file(), "payload " + payload + " is already named by asset " + claimant));
            } else {
                namedBy.put(sidecar.path(), sidecar.id());
            }
        }
        return refused;
    }
}
