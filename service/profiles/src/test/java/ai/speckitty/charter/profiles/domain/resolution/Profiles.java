package ai.speckitty.charter.profiles.domain.resolution;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.ArtifactReference;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import ai.speckitty.charter.profiles.domain.ModeDefault;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fixtures of the folds' tests: abridged profiles, and a profile's content as the field table reads it. */
final class Profiles {

    static final Specialization REVIEW = new Specialization(
            "Code quality",
            Optional.of("Architectural constraints"),
            Optional.of("Implementing changes"),
            Optional.empty());

    static final Collaboration REVIEW_COLLABORATION = new Collaboration(
            List.of("implementer", "planner"),
            List.of("implementer"),
            List.of(),
            List.of("review-report"),
            List.of(),
            List.of("review", "approve"));

    /** The directives a built-in profile writes, bare, since built-in holds them. */
    static final DirectiveReference D001 =
            new DirectiveReference("DIRECTIVE_001", "Architectural Integrity Standard", "Enforce boundaries");

    static final DirectiveReference D030 =
            new DirectiveReference("DIRECTIVE_030", "Test and Typecheck Quality Gate", "Gates are green");

    /** The same directives as an enhancer of another pack writes them: qualified with built-in (#673). */
    static final DirectiveReference BUILT_IN_D001 = qualified("built-in", D001);

    static final DirectiveReference BUILT_IN_D030 = qualified("built-in", D030);

    /** The directive org-base holds, as org-base writes it, bare. */
    static final DirectiveReference D038 =
            new DirectiveReference("DIRECTIVE_038", "Structured Prompt Change-Boundary", "Stay inside the canvas");

    /** A directive reference as a profile of another pack writes it: {@code <pack>::<code>}. */
    static DirectiveReference qualified(String pack, DirectiveReference reference) {
        return new DirectiveReference(pack + "::" + reference.code(), reference.name(), reference.rationale());
    }

    private Profiles() {}

    /** A profile with only the required fields. */
    static AgentProfile.Builder minimal(String id, String pack, Specialization specialization) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(new PackIdentifier(pack))
                .name("Name of " + id)
                .role("reviewer")
                .purpose("Purpose of " + id)
                .specialization(specialization);
    }

    /** An abridged reviewer: a field of each kind, scalar, list, object and list of objects. */
    static AgentProfile.Builder reviewer(String id, String pack) {
        return minimal(id, pack, REVIEW)
                .description(Optional.of("Quality assurance"))
                .capability("code-review")
                .capability("security-audit")
                .routingPriority(Optional.of(50))
                .maxConcurrentTasks(Optional.of(8))
                .collaboration(REVIEW_COLLABORATION)
                .modeDefault(new ModeDefault("code-review", "Line-by-line review", "Pull requests"))
                .specializationContext(new SpecializationContext(
                        List.of("java", "python"), List.of("spring"), List.of(), List.of(), List.of(), List.of()))
                .directiveReference(D001)
                .directiveReference(D030)
                .tacticReference(new ArtifactReference("code-review-incremental", "Read intent first"));
    }

    /** The profile's content: every field the table lists, by schema name, as its shape; absent fields are left out. */
    static Map<String, Object> content(AgentProfile profile) {
        Map<String, Object> content = new LinkedHashMap<>();
        for (ProfileField field : ProfileField.values()) {
            Object shape = field.shapeOf(profile);
            if (shape != null) {
                content.put(field.yamlName(), shape);
            }
        }
        return content;
    }

    /** The content without the identity fields. */
    static Map<String, Object> nonIdentity(AgentProfile profile) {
        Map<String, Object> content = content(profile);
        for (ProfileField field : ProfileField.values()) {
            if (field.identity()) {
                content.remove(field.yamlName());
            }
        }
        return content;
    }
}
