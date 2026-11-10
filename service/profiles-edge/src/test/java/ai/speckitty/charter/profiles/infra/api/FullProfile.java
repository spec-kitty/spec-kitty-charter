package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.ArtifactReference;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.ModeDefault;
import ai.speckitty.charter.profiles.domain.SelfReviewStep;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A profile that sets every field the domain holds, with distinct values, for the mapping tests. */
final class FullProfile {

    private FullProfile() {}

    /**
     * The builder with every field set and the exclusion left to the caller.
     *
     * @return a builder
     */
    static AgentProfile.Builder builder() {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier("full"))
                .packId(new PackIdentifier("built-in"))
                .schemaVersion(Optional.of("2"))
                .name("Full")
                .description(Optional.of("Every field"))
                .role("implementer")
                .altRole("reviewer")
                .avatarImage(Optional.of("avatars/full.png"))
                .capability("cap-one")
                .capability("cap-two")
                .routingPriority(Optional.of(80))
                .maxConcurrentTasks(Optional.of(3))
                .model(Optional.of("a-model"))
                .effort(Optional.of("high"))
                .purpose("Purpose")
                .specialization(
                        new Specialization("focus", Optional.of("aware"), Optional.of("avoid"), Optional.of("done")))
                .collaboration(new Collaboration(
                        List.of("to-1", "to-2"),
                        List.of("from-1"),
                        List.of("with-1"),
                        List.of("out-1"),
                        List.of("proc-1"),
                        List.of("verb-1")))
                .modeDefault(new ModeDefault("mode-1", "describes", "used when"))
                .modeDefault(new ModeDefault("mode-2", "describes too", "used then"))
                .initializationDeclaration(Optional.of("I am Full."))
                .sentinel(Optional.of(true))
                .specializationContext(new SpecializationContext(
                        List.of("java", "kotlin"),
                        List.of("spring"),
                        List.of("src/**"),
                        List.of("maven"),
                        List.of("pragmatic", "terse"),
                        List.of("low", "high")))
                .appliesToLanguage("java")
                .appliesToLanguage("kotlin")
                .directiveReference(new DirectiveReference("034", "Test-First", "why one"))
                .directiveReference(new DirectiveReference("010", "Fidelity", "why two"))
                .tacticReference(new ArtifactReference("tactic-1", "because t"))
                .toolguideReference(new ArtifactReference("toolguide-1", "because g"))
                .styleguideReference(new ArtifactReference("styleguide-1", "because s"))
                .selfReviewStep(new SelfReviewStep("compile", Optional.of("mvn compile"), "no errors"))
                .selfReviewStep(new SelfReviewStep("judge", Optional.empty(), "it reads well"));
    }

    /**
     * The profile with the object form of {@code excluding}.
     *
     * @return the profile
     */
    static AgentProfile full() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("alt-roles", List.of("reviewer"));
        values.put("capabilities", List.of());
        return builder()
                .excluding(Optional.of(new Exclusion.FieldValues(values)))
                .build();
    }
}
