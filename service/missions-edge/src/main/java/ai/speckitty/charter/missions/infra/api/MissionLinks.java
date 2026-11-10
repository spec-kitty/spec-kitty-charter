package ai.speckitty.charter.missions.infra.api;

import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.api.ArtifactsApi;
import ai.speckitty.charter.api.EffectiveMissionTypesApi;
import ai.speckitty.charter.api.MissionTypesApi;
import ai.speckitty.charter.api.model.Link;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Builds the links of the mission-type reads (ADR-0019 D3): every href is the configured base path
 * ({@code charter.api.base-path}, {@code /api/v1} by default) followed by the path template of the operation it names,
 * taken from the generated contract interfaces ({@code *Api.PATH_*}), so a path the contract changes fails here at
 * compile time or in a test, never in a client.
 *
 * <p>Each variable of a template is one percent-encoded path segment, so an identifier stays one segment. A skeleton's
 * name keeps its {@code ~}, which is unreserved: {@code a~01b.md} is written as is. A {@code ;}, which a path segment may
 * hold but which the servlet stack reads as the start of path parameters and drops with what follows, is written
 * {@code %3B}, so {@code a;b.md} is read back as itself. A {@code \} is written {@code %5C}, as any character a segment
 * may not hold; the embedded Tomcat refuses an encoded backslash (400), and the contract caps a name at 255 characters,
 * so {@link Scope#servedSkeleton} links no name that holds a backslash or is longer than that: a link names a route the
 * service answers.
 */
@Component
final class MissionLinks {

    private static final String MISSION_STEP_CONTRACT = "mission_step_contract";
    private static final int MAX_SKELETON_NAME = 255;
    private static final String KIND_QUERY = "?kind=";

    /** The path templates of a pack's reads. */
    private static final Templates PACK = new Templates(
            MissionTypesApi.PATH_GET_PACK_MISSION_TYPE,
            MissionTypesApi.PATH_GET_PACK_MISSION_STEP,
            MissionTypesApi.PATH_GET_PACK_MISSION_SKELETON,
            ArtifactsApi.PATH_LIST_PACK_ARTIFACTS,
            ArtifactsApi.PATH_GET_PACK_ARTIFACT);

    /** The path templates of an activation's reads. */
    private static final Templates ACTIVATION = new Templates(
            EffectiveMissionTypesApi.PATH_GET_EFFECTIVE_MISSION_TYPE,
            EffectiveMissionTypesApi.PATH_GET_EFFECTIVE_MISSION_STEP,
            EffectiveMissionTypesApi.PATH_GET_EFFECTIVE_MISSION_SKELETON,
            ActivationApi.PATH_LIST_EFFECTIVE_ARTIFACTS,
            ActivationApi.PATH_GET_EFFECTIVE_ARTIFACT);

    private final String basePath;

    /**
     * A builder for the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    MissionLinks(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The links of the reads of one pack.
     *
     * @param packId the pack's identifier, as the request names it
     * @return the pack's scope
     */
    Scope pack(String packId) {
        return new Scope(PACK, packId);
    }

    /**
     * The links of the reads under one activation: every link stays under it.
     *
     * @param activationId the activation's id, as the request names it
     * @return the activation's scope
     */
    Scope activation(String activationId) {
        return new Scope(ACTIVATION, activationId);
    }

    /**
     * The path templates of one scope, each starting with the scope's own variable ({@code {packId}} or
     * {@code {activationId}}).
     */
    private record Templates(String missionType, String step, String skeleton, String artifacts, String artifact) {}

    /**
     * The links of the mission-type reads of one scope, a pack or an activation: the same reads under
     * {@code /packs/{packId}} or {@code /activations/{activationId}}.
     */
    final class Scope {

        private final Templates templates;
        private final String id;

        private Scope(Templates templates, String id) {
            this.templates = templates;
            this.id = id;
        }

        /** The scope's mission type. */
        Link missionType(String missionTypeId) {
            return link(templates.missionType(), id, missionTypeId);
        }

        /** The scope's artifacts of kind {@code mission_type}. */
        Link missionTypes() {
            return new Link(href(templates.artifacts(), id) + KIND_QUERY + "mission_type");
        }

        /** One step of the scope's mission type. */
        Link step(String missionTypeId, String stepId) {
            return link(templates.step(), id, missionTypeId, stepId);
        }

        /** One skeleton of the scope's mission type, by its name (D9). */
        Link skeleton(String missionTypeId, String name) {
            return link(templates.skeleton(), id, missionTypeId, name);
        }

        /**
         * The link to a skeleton the service can serve: empty for a name that holds a {@code \} (Tomcat answers 400 to its
         * encoding) or is longer than the {@code skeletonName} parameter's 255 characters.
         */
        Optional<Link> servedSkeleton(String missionTypeId, String name) {
            return name.indexOf('\\') >= 0 || name.length() > MAX_SKELETON_NAME
                    ? Optional.empty()
                    : Optional.of(skeleton(missionTypeId, name));
        }

        /**
         * The scope's step contract: the generic item read of a pack, or {@code getEffectiveMissionStepContract} under an
         * activation (its literal path is the generic read's with the kind spelled).
         */
        Link stepContract(String stepContractId) {
            return artifact(MISSION_STEP_CONTRACT, stepContractId);
        }

        /** The scope's artifacts of kind {@code mission_step_contract}. */
        Link stepContracts() {
            return new Link(href(templates.artifacts(), id) + KIND_QUERY + MISSION_STEP_CONTRACT);
        }

        /** The scope's read of one artifact of any kind: the generic item read, or the generic effective read. */
        Link artifact(String kindToken, String artifactId) {
            return link(templates.artifact(), id, kindToken, artifactId);
        }
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

    /**
     * One path segment, percent-encoded so that it stays one segment and reads back as itself: as RFC 3986 encodes a
     * segment, and {@code ;} too.
     *
     * @param value the segment's value
     * @return the encoded segment
     */
    static String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8).replace(";", "%3B");
    }

    private Link link(String template, String... values) {
        return new Link(href(template, values));
    }

    /** The base path and the template, each of its variables replaced, in order, by one encoded segment. */
    private String href(String template, String... values) {
        StringBuilder path = new StringBuilder(basePath);
        int next = 0;
        for (String part : template.substring(1).split("/", -1)) {
            path.append('/');
            if (part.startsWith("{") && part.endsWith("}")) {
                path.append(segment(values[next++]));
            } else {
                path.append(part);
            }
        }
        if (next != values.length) {
            throw new IllegalArgumentException(template + " takes " + next + " values, not " + values.length);
        }
        return path.toString();
    }
}
