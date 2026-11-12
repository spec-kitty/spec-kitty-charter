package ai.speckitty.charter.contracts.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * {@code EffectiveArtifactRead}, {@code EffectiveGovernanceEntry} and {@code EffectiveAgentProfileRead} copy the members of
 * {@code EffectiveArtifact} (the schemas are closed and carry no {@code allOf} of it, so that a read can add its own
 * members): a member of the item that one of them lacks, or spells differently, would make the same fact read two ways
 * under one activation. Each copy repeats every member of the item, with the same schema, except what the item or the copy
 * says apart: {@code _links}, which the copies require and the item leaves optional (the item links only the kinds the
 * pack-scoped routes serve), and {@code suggestion}, which a governance entry replaces with {@code edge}.
 *
 * <p>{@code EffectiveContextArtifact}, the item of the effective action context, is a copy of another sort: it is an item
 * too, so it repeats every member of the item, {@code _links} included and optional as the item's, and adds the one
 * member of its own, the required {@code appliesThrough} (mission effective-step-and-action-context, FR-009).
 */
class EffectiveArtifactCopiesParityTest {

    private static final Path SCHEMAS = Path.of(System.getProperty("charter.charterSchemas"));
    private static final String ITEM = "EffectiveArtifact";
    private static final List<String> COPIES =
            List.of("EffectiveArtifactRead", "EffectiveGovernanceEntry", "EffectiveAgentProfileRead");
    private static final List<String> APART = List.of("_links");
    private static final Map<String, List<String>> ABSENT_BY_DESIGN =
            Map.of("EffectiveGovernanceEntry", List.of("suggestion"));
    private static final String CONTEXT_ITEM = "EffectiveContextArtifact";

    @Test
    void theContextItemRepeatsEveryMemberOfTheItemAndItsRequiredMembersAndAddsAppliesThroughAlone() {
        Map<String, Object> item = properties(ITEM);
        Map<String, Object> copied = properties(CONTEXT_ITEM);

        for (Map.Entry<String, Object> member : item.entrySet()) {
            assertThat(copied)
                    .as("%s has the member %s of %s", CONTEXT_ITEM, member.getKey(), ITEM)
                    .containsEntry(member.getKey(), member.getValue());
        }
        assertThat(copied.keySet())
                .as("the one member of its own")
                .containsExactlyInAnyOrderElementsOf(concat(item.keySet(), List.of("appliesThrough")));
        assertThat(required(CONTEXT_ITEM))
                .as("the required members of %s", CONTEXT_ITEM)
                .containsExactlyInAnyOrderElementsOf(concat(required(ITEM), List.of("appliesThrough")));
    }

    @Test
    void theAppliesThroughOfTheContextItemIsOneOfTheThreeRulesOfTheOwnershipRule() {
        Map<?, ?> appliesThrough = (Map<?, ?>) properties(CONTEXT_ITEM).get("appliesThrough");

        assertThat(((List<?>) appliesThrough.get("enum"))
                        .stream().map(String::valueOf).toList())
                .containsExactly("action", "mission_type", "project");
        assertThat(appliesThrough.containsKey("x-derived"))
                .as("appliesThrough cites its rule")
                .isTrue();
    }

    private static List<String> concat(java.util.Collection<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    @Test
    void everyCopyRepeatsTheMembersOfTheItemWithTheSameSchema() {
        Map<String, Object> item = properties(ITEM);
        for (String copy : COPIES) {
            Map<String, Object> copied = properties(copy);
            for (Map.Entry<String, Object> member : item.entrySet()) {
                String name = member.getKey();
                if (APART.contains(name)
                        || ABSENT_BY_DESIGN.getOrDefault(copy, List.of()).contains(name)) {
                    continue;
                }
                assertThat(copied)
                        .as("%s has the member %s of %s", copy, name, ITEM)
                        .containsKey(name);
                assertThat(copied.get(name))
                        .as("the member %s of %s is the one of %s", name, copy, ITEM)
                        .isEqualTo(member.getValue());
            }
        }
    }

    @Test
    void everyCopyRequiresWhatTheItemRequiresAndLinks() {
        List<String> required = required(ITEM);
        for (String copy : COPIES) {
            assertThat(required(copy)).as("the required members of %s", copy).containsAll(required);
            assertThat(properties(copy)).as("%s holds _links", copy).containsKey("_links");
            assertThat(required(copy)).as("%s requires _links", copy).contains("_links");
        }
        assertThat(properties(ITEM)).as("the item holds _links").containsKey("_links");
        assertThat(required)
                .as("the item leaves _links optional: it links only the kinds the pack-scoped routes serve")
                .doesNotContain("_links");
    }

    @Test
    void theGovernanceEntryHasNoSuggestionBecauseItHasAnEdge() {
        assertThat(properties("EffectiveGovernanceEntry")).containsKey("edge").doesNotContainKey("suggestion");
        assertThat(properties("EffectiveArtifactRead")).containsKey("suggestion");
        assertThat(properties("EffectiveAgentProfileRead")).containsKey("suggestion");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(String schema) {
        return new LinkedHashMap<>((Map<String, Object>) load(schema).get("properties"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(String schema) {
        return new ArrayList<>((List<String>) load(schema).get("required"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String schema) {
        try {
            String text = Files.readString(SCHEMAS.resolve(schema + ".yaml"), StandardCharsets.UTF_8);
            return (Map<String, Object>) new Load(LoadSettings.builder().build()).loadFromString(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
