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
 * {@code GlossaryTerm} (a pack's term read), {@code GlossaryContextTerm} (a term inside its context) and
 * {@code VocabularyTerm} (a term in force under an activation) are three closed schemas of one concept, the term of a
 * glossary context (#106; ADR-0049 decision 3): each repeats the term's members, which the contract copies by hand and
 * no {@code allOf} shares. A member one of them lacks, names differently or types differently would make the same
 * term read two ways, so each copy holds every term member with the same schema, and requires the same of them (the
 * term members the pack schema requires). What a copy adds beside them ({@code urn}, {@code contextId}, the holding
 * pack, {@code _links}) is its own.
 */
class TermCopiesParityTest {

    private static final Path SCHEMAS = Path.of(System.getProperty("charter.charterSchemas"));
    private static final List<String> COPIES = List.of("GlossaryTerm", "GlossaryContextTerm", "VocabularyTerm");
    private static final List<String> TERM_MEMBERS =
            List.of("id", "name", "definition", "examples", "aliases", "avoid", "status", "seeAlso");
    private static final List<String> REQUIRED_TERM_MEMBERS = List.of("id", "name", "definition", "status");

    @Test
    void everyCopyHoldsEveryTermMemberWithTheSameSchema() {
        Map<String, Object> reference = properties(COPIES.get(0));
        for (String copy : COPIES) {
            Map<String, Object> copied = properties(copy);
            for (String member : TERM_MEMBERS) {
                assertThat(copied).as("%s has the term member %s", copy, member).containsKey(member);
                assertThat(copied.get(member))
                        .as("the member %s of %s is the one of %s", member, copy, COPIES.get(0))
                        .isEqualTo(reference.get(member));
            }
        }
    }

    @Test
    void everyCopyRequiresTheSameTermMembers() {
        for (String copy : COPIES) {
            List<String> required = required(copy);
            required.retainAll(TERM_MEMBERS);
            assertThat(required)
                    .as("the term members %s requires", copy)
                    .containsExactlyInAnyOrderElementsOf(REQUIRED_TERM_MEMBERS);
        }
    }

    @Test
    void theTermsIdIsIdInEveryCopyAndNoCopySpellsItAnotherWay() {
        // One name per concept: the term's id is id beside contextId and urn, never termId (operator ruling
        // 2026-11-14).
        for (String copy : COPIES) {
            assertThat(properties(copy)).as(copy).containsKey("id").doesNotContainKey("termId");
        }
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
