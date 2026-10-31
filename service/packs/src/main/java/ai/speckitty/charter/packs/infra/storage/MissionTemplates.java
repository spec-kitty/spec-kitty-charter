package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.TemplateReference;
import ai.speckitty.charter.missions.domain.UnresolvedTemplate;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Template;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A mission type's output templates resolved against the pack's templates (#277, ADR-0044 decision 7 as amended): the
 * one place the pack store enforces it, at assembly ({@code DomainRepositoryService.assembly}) and before a whole-pack
 * write. The missions domain resolves a {@code template_path} against template ids
 * ({@link MissionType#unresolvedTemplates}); this is the pack's side, which knows the templates and words the refusal. A path resolves only to a mission type's own template, one filed under
 * {@code missions/<type>/templates/}: a template of the same id under the pack's top-level {@code templates/} is written
 * there, where the path does not lead.
 */
final class MissionTemplates {

    private MissionTemplates() {}

    /**
     * The ids a mission type's output template can name: those of the mission types' own templates.
     *
     * @param templates the pack's templates
     * @return their ids, {@code <type>/<path>}
     */
    static Set<String> namable(Collection<Template> templates) {
        return templates.stream()
                .filter(template -> template.missionType().isPresent())
                .map(template -> template.id().value())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The first output template an artifact names that the pack does not hold.
     *
     * @param artifact the artifact; only a mission type names templates
     * @param namable the ids it may name ({@link #namable})
     * @return the unresolved template; empty when every one resolves, or the artifact is no mission type
     */
    static Optional<UnresolvedTemplate> firstUnresolved(CharterArtifact artifact, Set<String> namable) {
        return artifact instanceof MissionType type
                ? type.unresolvedTemplates(namable).stream().findFirst()
                : Optional.empty();
    }

    /**
     * The reason of an unresolved template, as the store words a named file's refusal: {@code <path> (named by step
     * '<id>'): is not a template of the type}, or {@code (named by the type)}. The path names no template the type owns:
     * none is there, it failed to read as one, or it is filed as another type's or under the pack's top-level
     * {@code templates/}.
     *
     * @param unresolved the unresolved template
     * @return the reason
     */
    static String reason(UnresolvedTemplate unresolved) {
        TemplateReference reference = unresolved.reference();
        String namedBy = reference.step().map(id -> "step '" + id + "'").orElse("the type");
        return reference.templatePath() + " (named by " + namedBy + "): is not a template of the type";
    }

    /**
     * Refuses a pack about to be written whose mission type names a template the aggregate does not hold, before any
     * byte is written: the reload would refuse the type.
     *
     * @param pack the pack
     * @throws CharterRefusal with {@code VALIDATION_FAILED}, {@code mission_type '<id>' names <reason>}, the store's
     *     wording for a per-file write
     */
    static void requireResolved(Pack pack) {
        Set<String> namable = namable(pack.templates());
        for (CharterArtifact artifact : pack.artifacts()) {
            firstUnresolved(artifact, namable).ifPresent(unresolved -> {
                throw new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED,
                        "mission_type '" + unresolved.missionType().value() + "' names " + reason(unresolved));
            });
        }
    }
}
