package ai.speckitty.charter.profiles.domain;

import java.util.List;

/**
 * When an {@link AgentProfile} is the preferred specialist: its {@code specialization-context}. Every list keeps its
 * authored order and duplicates; a profile that declares none of them has {@link #NONE}.
 *
 * @param languages the languages
 * @param frameworks the frameworks
 * @param filePatterns the file patterns ({@code file-patterns})
 * @param domainKeywords the domain keywords ({@code domain-keywords})
 * @param writingStyle the writing styles ({@code writing-style})
 * @param complexityPreference the complexity preferences ({@code complexity-preference})
 */
public record SpecializationContext(
        List<String> languages,
        List<String> frameworks,
        List<String> filePatterns,
        List<String> domainKeywords,
        List<String> writingStyle,
        List<String> complexityPreference) {

    /** No specialization context declared. */
    public static final SpecializationContext NONE =
            new SpecializationContext(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    /**
     * Copies the lists.
     *
     * @param languages the languages
     * @param frameworks the frameworks
     * @param filePatterns the file patterns
     * @param domainKeywords the domain keywords
     * @param writingStyle the writing styles
     * @param complexityPreference the complexity preferences
     */
    public SpecializationContext {
        languages = List.copyOf(languages);
        frameworks = List.copyOf(frameworks);
        filePatterns = List.copyOf(filePatterns);
        domainKeywords = List.copyOf(domainKeywords);
        writingStyle = List.copyOf(writingStyle);
        complexityPreference = List.copyOf(complexityPreference);
    }
}
