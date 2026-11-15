package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.ProfileSelectionCandidate;
import ai.speckitty.charter.api.model.ProfileSelectionContext;
import ai.speckitty.charter.api.model.ProfileSelectionRead;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.profiles.application.ProfileSelectionService.Selection;
import ai.speckitty.charter.profiles.application.ProfileSelectionService.Subject;
import ai.speckitty.charter.profiles.domain.selection.Fit;
import ai.speckitty.charter.profiles.domain.selection.Pick;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of a profile selection ({@code selectAgentProfile}, and {@code selectAgentProfileForStep}, whose role
 * is the step's and absent for a step that names none): the asked role and context, each picked profile
 * with its pack and fit, and whether they tie. The links are built under the base path the controllers are mapped
 * under, the way {@link EffectiveProfileDtos} builds its own: the answer's {@code self} is the request, with the context
 * it asked and nothing else, and each candidate links {@code effective-artifact}, its read under the activation.
 */
@Component
public class ProfileSelectionDtos {

    private static final String ACTIVATIONS = "activations";
    private static final String PROFILE = ArtifactKind.AGENT_PROFILE.token();
    private static final String SELECTION = "profile-selection";

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public ProfileSelectionDtos(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The answer of a selection.
     *
     * @param selection what the application ring selected
     * @return the role, the context, the candidates and the tie
     */
    public ProfileSelectionRead read(Selection selection) {
        ProfileSelectionContext context = new ProfileSelectionContext();
        selection.context().language().ifPresent(context::setLanguage);
        selection.context().framework().ifPresent(context::setFramework);
        ProfileSelectionRead read = new ProfileSelectionRead(
                context,
                selection.answer().picks().stream()
                        .map(pick -> candidate(pick, selection.activationId()))
                        .toList(),
                selection.answer().tie(),
                Map.of("self", new Link(self(selection))));
        selection.subject().role().ifPresent(read::setRole);
        return read;
    }

    private ProfileSelectionCandidate candidate(Pick pick, String activationId) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(
                "effective-artifact",
                new Link(href(ACTIVATIONS, activationId, "artifacts", PROFILE, pick.profileId())));
        return new ProfileSelectionCandidate(pick.profileId(), pick.pack(), fit(pick.fit()), links);
    }

    /** The wire token of a fit, by an exhaustive switch and never by name. */
    static ProfileSelectionCandidate.FitEnum fit(Fit fit) {
        return switch (fit) {
            case PRIMARY -> ProfileSelectionCandidate.FitEnum.PRIMARY;
            case ALT -> ProfileSelectionCandidate.FitEnum.ALT;
            case FALLBACK -> ProfileSelectionCandidate.FitEnum.FALLBACK;
        };
    }

    /**
     * The request: the role's path, or the step's, and the asked context, language first, each query value
     * percent-encoded.
     */
    private String self(Selection selection) {
        List<String> query = new ArrayList<>();
        selection.context().language().ifPresent(language -> query.add("language=" + encoded(language)));
        selection.context().framework().ifPresent(framework -> query.add("framework=" + encoded(framework)));
        String path = switch (selection.subject()) {
            case Subject.Role role -> href(ACTIVATIONS, selection.activationId(), "roles", role.name(), SELECTION);
            case Subject.Step step ->
                href(
                        ACTIVATIONS,
                        selection.activationId(),
                        "mission-types",
                        step.missionType(),
                        "actions",
                        step.action(),
                        SELECTION);
        };
        return query.isEmpty() ? path : path + "?" + String.join("&", query);
    }

    private static String encoded(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
