package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** The ADR-0066 pack shapes: enhancement, fragment, slot gate, activation and graph vocabulary. */
class PromptFragmentContractsTest {

    private static final PackSchemas SCHEMAS = PackSchemas.bundled();
    private static final String HASH = "a".repeat(64);

    private static List<SchemaViolation> yaml(String kind, String text) {
        return SCHEMAS.validate(kind, "probe.yaml", Yaml.load(text));
    }

    private static String schema(String kind) throws IOException {
        try (InputStream in =
                PromptFragmentContractsTest.class.getResourceAsStream("/packs/schemas/" + kind + ".schema.json")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void anEnhancementNamesOneSlotAndItsCompanionWithoutSecondTargetingOrOrderingFields() {
        String valid = """
                schema_version: "1.0"
                id: spdd-specify
                prompt_fragments:
                  - slot: spdd:reasons-block
                    path: spdd-specify.prompt-fragment.md
                """;

        assertThat(yaml("step-enhancement", valid)).isEmpty();
        assertThat(yaml("step-enhancement", valid + "target: built-in::action:software-dev/specify\n"))
                .singleElement()
                .satisfies(violation -> assertThat(violation.property()).contains("target"));
        assertThat(yaml("step-enhancement", valid + "priority: 1\n"))
                .singleElement()
                .satisfies(violation -> assertThat(violation.property()).contains("priority"));
        assertThat(yaml("step-enhancement", "schema_version: \"1.0\"\nid: guards-only\n"))
                .as("an enhancement without fragments stays valid")
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SPDD:reasons-block",
                "spdd",
                "spdd:",
                "spdd:reasons_block",
                "spdd:reasons-block:extra",
                "1spdd:reasons-block"
            })
    void aSlotIdHasTwoLowerCaseParts(String slot) {
        String enhancement = """
                schema_version: "1.0"
                id: spdd-specify
                prompt_fragments:
                  - slot: '%s'
                    path: spdd-specify.prompt-fragment.md
                """.formatted(slot);

        assertThat(yaml("step-enhancement", enhancement)).singleElement().satisfies(violation -> {
            assertThat(violation.keyword()).isEqualTo("pattern");
            assertThat(violation.pointer()).containsExactly("prompt_fragments", "0", "slot");
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../spdd.prompt-fragment.md",
                "nested/spdd.prompt-fragment.md",
                "spdd.md",
                "SPDD.prompt-fragment.md"
            })
    void aFragmentCompanionIsALowerCaseMarkdownFileBesideTheEnhancement(String path) {
        String enhancement = """
                schema_version: "1.0"
                id: spdd-specify
                prompt_fragments:
                  - slot: spdd:reasons-block
                    path: '%s'
                """.formatted(path);

        assertThat(yaml("step-enhancement", enhancement))
                .singleElement()
                .satisfies(violation -> assertThat(violation.keyword()).isEqualTo("pattern"));
    }

    @Test
    void aFragmentHasNonBlankMarkdownAndNoFrontmatter() {
        MarkdownDocument preamble = new MarkdownDocument(Optional.empty(), Optional.empty(), "Guidance.", List.of());
        MarkdownDocument heading = new MarkdownDocument(
                Optional.empty(), Optional.empty(), "", List.of(new MarkdownDocument.Section("REASONS guidance", "")));
        MarkdownDocument h1 = new MarkdownDocument(Optional.empty(), Optional.of("Guidance"), "", List.of());
        MarkdownDocument blank = MarkdownDocument.empty();
        MarkdownDocument frontmatter = new MarkdownDocument(
                Optional.of(Map.of("description", "not allowed")), Optional.empty(), "Text", List.of());

        assertThat(SCHEMAS.validateMarkdown("prompt-fragment", "spdd.prompt-fragment.md", preamble))
                .isEmpty();
        assertThat(SCHEMAS.validateMarkdown("prompt-fragment", "spdd.prompt-fragment.md", heading))
                .isEmpty();
        assertThat(SCHEMAS.validateMarkdown("prompt-fragment", "spdd.prompt-fragment.md", h1))
                .isEmpty();
        assertThat(SCHEMAS.validateMarkdown("prompt-fragment", "spdd.prompt-fragment.md", blank))
                .isNotEmpty();
        assertThat(SCHEMAS.validateMarkdown("prompt-fragment", "spdd.prompt-fragment.md", frontmatter))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.keyword()).isEqualTo("additionalProperties");
                    assertThat(violation.property()).contains("frontmatter");
                });
    }

    @Test
    void descriptor10IsTheEmptySlotVersionAndAnUnversionedDescriptorIsTheLegacyShape() {
        String bare = "pack_id: 01ARZ3NDEKTSV4RRFFQ69G5FAV\nname: demo\npack_version: 1.0.0\n";

        assertThat(yaml("pack-descriptor", bare)).isEmpty();
        assertThat(yaml("pack-descriptor", "schema_version: \"1.0\"\n" + bare)).isEmpty();
        assertThat(yaml("pack-descriptor", "schema_version: \"1.1\"\n" + bare))
                .singleElement()
                .satisfies(violation -> assertThat(violation.keyword()).isEqualTo("const"));
        assertThat(yaml("pack-descriptor", "schema_version: 1.0\n" + bare))
                .extracting(SchemaViolation::keyword)
                .containsExactlyInAnyOrder("type", "const");
    }

    @Test
    void aPresetActivatesStepEnhancements() {
        String preset = """
                schema_version: "1.0"
                id: spdd
                description: Select SPDD and its prompt enhancements.
                activated_step_enhancements:
                  - id: spdd-specify
                """;

        assertThat(yaml("preset", preset)).isEmpty();
        assertThat(yaml("preset", preset.replace("  - id: spdd-specify", "  []").replace(":\n  []", ": []")))
                .isNotEmpty();
    }

    @Test
    void graphDocumentsAndTheManifestCarryTheEnhancementKind() {
        String shard = """
                schema_version: "1.0"
                generated_at: STATIC
                nodes:
                  - urn: step_enhancement:spdd-specify
                    kind: step_enhancement
                edges:
                  - source: step_enhancement:spdd-specify
                    target: built-in::action:software-dev/specify
                    relation: enhances
                """;
        String fragment = """
                nodes:
                  - id: spdd-specify
                    kind: step_enhancements
                    body_path: missions/software-dev/enhancements/spdd-specify.step-enhancement.yaml
                edges: []
                """;
        String manifest = """
                schema_version: "1.0"
                manifest_hash: %1$s
                constituents:
                  - kind: step_enhancement
                    id: spdd-specify
                    path: missions/software-dev/enhancements/spdd-specify.step-enhancement.yaml
                    content_hash: %1$s
                """.formatted(HASH);

        assertThat(yaml("graph-shard", shard)).isEmpty();
        assertThat(yaml("org-fragment", fragment)).isEmpty();
        assertThat(yaml("pack-manifest", manifest)).isEmpty();
    }

    @Test
    void slotRulesAndTheirRefusalCodesAreRecordedBesideThePromptStructure() throws IOException {
        String prompt = JsonMapper.builder()
                .build()
                .readTree(schema("mission-step-prompt"))
                .path("description")
                .asString();
        String fragment = JsonMapper.builder()
                .build()
                .readTree(schema("prompt-fragment"))
                .path("description")
                .asString();

        assertThat(prompt)
                .contains("<!-- <slot-id>:start -->")
                .contains("[a-z][a-z0-9-]*")
                .contains("prompt_slot_malformed")
                .contains("prompt_slot_not_empty")
                .contains("schema_version \"1.0\"")
                .contains("warning");
        assertThat(fragment).contains("prompt_fragment_invalid").contains("slot marker");
    }
}
