package ai.speckitty.charter.missions.infra.api;

import ai.speckitty.charter.api.model.Link;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Builds the links of the mission-type reads (ADR-0019 D3): every href is the configured base path
 * ({@code charter.api.base-path}, {@code /api/v1} by default) followed by a path whose segments are percent-encoded, so
 * an identifier stays one segment. A skeleton's name keeps its {@code ~}, which is unreserved: {@code a~01b.md} is
 * written as is.
 */
@Component
final class MissionLinks {

    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String MISSION_TYPE = "mission_type";
    private static final String MISSION_STEP_CONTRACT = "mission_step_contract";

    private final String basePath;

    /**
     * A builder for the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    MissionLinks(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /** A pack's mission type. */
    Link missionType(String packId, String missionTypeId) {
        return link(PACKS, packId, ARTIFACTS, MISSION_TYPE, missionTypeId);
    }

    /** A pack's artifacts of kind {@code mission_type}. */
    Link missionTypes(String packId) {
        return new Link(href(PACKS, packId, ARTIFACTS) + "?kind=" + MISSION_TYPE);
    }

    /** One step of a pack's mission type. */
    Link step(String packId, String missionTypeId, String stepId) {
        return link(PACKS, packId, ARTIFACTS, MISSION_TYPE, missionTypeId, "steps", stepId);
    }

    /** One skeleton of a pack's mission type, by its name (D9). */
    Link skeleton(String packId, String missionTypeId, String name) {
        return link(PACKS, packId, ARTIFACTS, MISSION_TYPE, missionTypeId, "skeletons", name);
    }

    /** A pack's step contract, read by the generic item read. */
    Link stepContract(String packId, String stepContractId) {
        return link(PACKS, packId, ARTIFACTS, MISSION_STEP_CONTRACT, stepContractId);
    }

    /**
     * The {@code _links} of an answer: each link under its relation's wire key, in the order of {@link MissionRelation}.
     *
     * @param links the links by relation
     * @return the links by wire key
     */
    static Map<String, Link> wire(EnumMap<MissionRelation, Link> links) {
        Map<String, Link> wire = new LinkedHashMap<>();
        links.forEach((relation, link) -> wire.put(relation.wireKey(), link));
        return wire;
    }

    private Link link(String... segments) {
        return new Link(href(segments));
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
