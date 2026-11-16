package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.Directionality;
import ai.speckitty.charter.kernel.domain.EndpointPair;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Tension;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.missions.domain.SubstepDelegation;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.KindTaxonomyReadService;
import ai.speckitty.charter.packs.application.KindTaxonomyReadService.HeldAs;
import ai.speckitty.charter.packs.application.KindTaxonomyReadService.KindFacts;
import ai.speckitty.charter.packs.application.KindTaxonomyReadService.RelationFacts;
import ai.speckitty.charter.packs.application.KindTaxonomyReadService.Taxonomy;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.rules.GraphBinding;
import ai.speckitty.charter.packs.domain.rules.GraphBinding.GraphBuild;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vocabulary of the bundled schemas and the domain's vocabulary are the same words (T024): a kind added in
 * {@code file-kinds.yaml} and the schemas has to be added to {@code NodeKind} too, or one of these fails. The one
 * place the two differ on purpose is {@code in_tension_with}, today's spelling of the tension relationship (ADR-0036
 * Decision 4): the schemas accept it as a relation, and the domain binds it to a {@code Tension} held beside the
 * relationships, not to a relation type (ADR-0028, ADR-0045).
 */
class VocabularyEqualityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TENSION_SPELLING = "in_tension_with";
    private static final Pattern ALTERNATION =
            Pattern.compile("^\\^(?:\\(\\[a-z0-9\\]\\[a-z0-9_-\\]\\*::\\)\\?)?\\(([a-z_|]+)\\):");
    private static final Pattern TARGET_ONLY_PREFIX =
            Pattern.compile("\\^\\(\\[a-z0-9\\]\\[a-z0-9_-\\]\\*::\\)\\?([a-z_]+):");

    private static JsonNode schema(String kind) throws IOException {
        try (InputStream in =
                VocabularyEqualityTest.class.getResourceAsStream("/packs/schemas/" + kind + ".schema.json")) {
            assertThat(in).as(kind).isNotNull();
            return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static Set<String> alternation(String pattern) {
        Matcher m = ALTERNATION.matcher(pattern);
        assertThat(m.find()).as("an alternation of kinds in %s", pattern).isTrue();
        return new TreeSet<>(Arrays.asList(m.group(1).split("\\|")));
    }

    /** The node kinds a graph document may declare: guard and lifecycle wait for their contract set (#156). */
    private static Set<String> declarableTokens() {
        return new TreeSet<>(Arrays.stream(NodeKind.values())
                .filter(NodeKind::declarableInGraphs)
                .map(NodeKind::token)
                .toList());
    }

    /** The node kinds an edge may name as target: guard and lifecycle wait for their contract set (#156). */
    private static Set<String> edgeTargetTokens() {
        return new TreeSet<>(Arrays.stream(NodeKind.values())
                .filter(NodeKind::edgeTarget)
                .map(NodeKind::token)
                .toList());
    }

    /** The node kinds an edge may name as source: the edge targets less {@code term}, only ever a vocabulary target. */
    private static Set<String> edgeSourceTokens() {
        return new TreeSet<>(Arrays.stream(NodeKind.values())
                .filter(NodeKind::edgeSource)
                .map(NodeKind::token)
                .toList());
    }

    // ---- node kinds -----------------------------------------------------------------------------

    @Test
    void theShardsNodeKindsAreTheDomainsAndTheFileKindTablesInEveryPlaceTheSchemaListsThem() throws IOException {
        JsonNode defs = schema("graph-shard").get("$defs");
        JsonNode node = defs.get("DRGNode").get("properties");
        Set<String> domain = declarableTokens();
        Set<String> edgeTargets = edgeTargetTokens();

        assertThat(new TreeSet<>(texts(node.get("kind").get("enum"))))
                .as("DRGNode.kind enum")
                .isEqualTo(domain);
        assertThat(alternation(node.get("urn").get("pattern").asString()))
                .as("DRGNode.urn alternation")
                .isEqualTo(domain);
        JsonNode edge = defs.get("DRGEdge").get("properties");
        assertThat(alternation(edge.get("source").get("pattern").asString()))
                .as("edge source")
                .isEqualTo(edgeSourceTokens());
        assertThat(alternation(edge.get("target").get("pattern").asString()))
                .as("edge target")
                .isEqualTo(edgeTargets);
        Set<String> branches = new TreeSet<>();
        defs.get("DRGNode").get("allOf").forEach(branch -> {
            String constant =
                    branch.get("if").get("properties").get("kind").get("const").asString();
            branches.add(constant);
            assertThat(branch.get("then")
                            .get("properties")
                            .get("urn")
                            .get("pattern")
                            .asString())
                    .as("the urn prefix of kind %s", constant)
                    .isEqualTo("^" + constant + ":");
        });
        assertThat(branches).as("one urn-prefix branch per kind").isEqualTo(domain);
        assertThat(PackFileKind.bundled().nodeKinds())
                .as("file-kinds.yaml node_kinds, in the order of NodeKind")
                .containsExactlyElementsOf(
                        Arrays.stream(NodeKind.values()).map(NodeKind::token).toList());
    }

    /**
     * A kind an edge may name only as a target, {@code term}, is a target of {@code vocabulary} alone in both edge
     * schemas, as in {@link NodeKind#edgeTargetOf}: the source alternation leaves it out, and one {@code if}/{@code
     * then} branch per such kind binds its relation to {@code vocabulary}.
     */
    @ParameterizedTest
    @CsvSource({"graph-shard,DRGEdge", "org-fragment,_OrgDRGEdge"})
    void aTargetOnlyKindIsTheTargetOfAVocabularyEdgeAloneInEveryEdgeSchema(String kind, String def) throws IOException {
        JsonNode edge = schema(kind).get("$defs").get(def);
        Set<String> targetOnly = new TreeSet<>(edgeTargetTokens());
        targetOnly.removeAll(edgeSourceTokens());

        assertThat(targetOnly).as("the target-only kinds").containsExactly("term");
        assertThat(alternation(
                        edge.get("properties").get("source").get("pattern").asString()))
                .as("edge source")
                .isEqualTo(edgeSourceTokens());
        assertThat(alternation(
                        edge.get("properties").get("target").get("pattern").asString()))
                .as("edge target")
                .isEqualTo(edgeTargetTokens());
        Set<String> branches = new TreeSet<>();
        edge.get("allOf").forEach(branch -> {
            String prefix = branch.get("if")
                    .get("properties")
                    .get("target")
                    .get("pattern")
                    .asString();
            assertThat(branch.get("then")
                            .get("properties")
                            .get("relation")
                            .get("const")
                            .asString())
                    .as("the relation of %s", prefix)
                    .isEqualTo("vocabulary");
            Matcher m = TARGET_ONLY_PREFIX.matcher(prefix);
            assertThat(m.matches()).as("a kind prefix: %s", prefix).isTrue();
            branches.add(m.group(1));
        });
        assertThat(branches)
                .as("one vocabulary-only branch per target-only kind")
                .isEqualTo(targetOnly);
    }

    // ---- fragment kinds ----------------------------------------------------------------------------

    @Test
    void theFragmentsKindEnumIsTheFileKindsPluralsAndTheDomainOfFromPlural() throws IOException {
        Set<String> enumValues = new TreeSet<>(texts(schema("org-fragment")
                .get("$defs")
                .get("_OrgDRGNode")
                .get("properties")
                .get("kind")
                .get("enum")));
        Map<String, String> table = PackFileKind.bundled().fragmentKinds();

        assertThat(enumValues).as("the schema's kind enum").isEqualTo(new TreeSet<>(table.keySet()));
        Set<String> domain = new TreeSet<>();
        for (NodeKind kind : NodeKind.values()) {
            if (!kind.declarableInGraphs()) {
                continue;
            }
            String plural = kind.artifactKind().map(ArtifactKind::plural).orElse(kind.token() + "s");
            if (NodeKind.fromPlural(plural).filter(kind::equals).isPresent()) {
                domain.add(plural);
            }
        }
        assertThat(domain).as("the plurals NodeKind.fromPlural answers").isEqualTo(enumValues);
        table.forEach((plural, token) -> assertThat(NodeKind.fromPlural(plural).map(NodeKind::token))
                .as("fromPlural(%s)", plural)
                .contains(token));
        assertThat(new TreeSet<>(table.values()))
                .as("each node kind has one plural")
                .isEqualTo(declarableTokens());
    }

    // ---- artifact kinds ----------------------------------------------------------------------------

    @Test
    void theFileKindsArtifactKindsAreTheDomainsArtifactKinds() throws IOException {
        Object table;
        try (InputStream in = VocabularyEqualityTest.class.getResourceAsStream("/packs/file-kinds.yaml")) {
            table = Yaml.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        List<?> kinds = (List<?>) ((Map<?, ?>) table).get("artifact_kinds");

        assertThat(kinds.stream().map(String::valueOf).toList())
                .containsExactlyElementsOf(Arrays.stream(ArtifactKind.values())
                        .map(ArtifactKind::token)
                        .toList());
    }

    @Test
    void thePackManifestsConstituentKindsAreTheDomainsArtifactKinds() throws IOException {
        Map<?, ?> schema;
        try (InputStream in =
                VocabularyEqualityTest.class.getResourceAsStream("/packs/schemas/pack-manifest.schema.json")) {
            schema = JsonMapper.builder().build().readValue(in, Map.class);
        }
        Map<?, ?> kind = (Map<?, ?>) ((Map<?, ?>)
                        ((Map<?, ?>) ((Map<?, ?>) schema.get("$defs")).get("constituent")).get("properties"))
                .get("kind");

        assertThat(((List<?>) kind.get("enum")).stream().map(String::valueOf).toList())
                .as("a manifest may list a constituent of every artifact kind (#194: mission types included)")
                .containsExactlyElementsOf(Arrays.stream(ArtifactKind.values())
                        .map(ArtifactKind::token)
                        .toList());
    }

    @Test
    void theInspectorsArtifactSchemaKindsAreTheMappedSchemasThatDeclareAnId() throws IOException {
        Set<String> expected = new TreeSet<>();
        for (PackFileKind.FileRule rule : PackFileKind.bundled().fileRules()) {
            try (InputStream in = VocabularyEqualityTest.class.getResourceAsStream(
                    "/packs/schemas/" + rule.schema() + ".schema.json")) {
                Map<?, ?> properties = (Map<?, ?>)
                        JsonMapper.builder().build().readValue(in, Map.class).get("properties");
                // A preset declares an id but is a pack-level file, not an artifact (#195, ADR-0042).
                if (properties != null
                        && properties.containsKey("id")
                        && !rule.schema().equals("preset")) {
                    expected.add(rule.schema());
                }
            }
        }

        assertThat(FileSystemPackInspector.artifactKinds()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(FileSystemPackInspector.artifactKinds())
                .as("the ten kinds that were listed by hand, the mission type (#194), the guard and the lifecycle")
                .containsExactlyInAnyOrder(
                        "mission-type",
                        "step-enhancement",
                        "guard",
                        "lifecycle",
                        "directive",
                        "tactic",
                        "styleguide",
                        "toolguide",
                        "paradigm",
                        "procedure",
                        "asset",
                        "glossary",
                        "skill");
    }

    @Test
    void aPayloadKindIsTheDomainsTemplateKindOrTheAssetPayloadAndNoneHasASchema() {
        List<String> payloadKinds = PackFileKind.bundled().payloadRules().stream()
                .map(PackFileKind.PayloadRule::kind)
                .toList();

        assertThat(payloadKinds)
                .as("a pack's top-level templates and a mission type's templates are both templates (#194)")
                .containsExactly("asset-payload", ArtifactKind.TEMPLATE.token(), ArtifactKind.TEMPLATE.token());
        assertThat(PackFileKind.bundled().fileRules())
                .extracting(PackFileKind.FileRule::schema)
                .as("a payload kind is not a schema kind, so PackSchemas never sees it")
                .doesNotContainAnyElementsOf(payloadKinds);
    }

    // ---- relations ---------------------------------------------------------------------------------

    @Test
    void theSchemasRelationsAreTheDomainsRelationsPlusExactlyTheTensionSpelling() throws IOException {
        // A derived relation type (implemented_by, ADR-0054 decision 3) is never authored: no document spells it.
        Set<String> domain = new TreeSet<>(Arrays.stream(RelationType.values())
                .filter(type -> !type.derived())
                .map(RelationType::token)
                .toList());
        Set<String> shard = new TreeSet<>(
                texts(schema("graph-shard").get("$defs").get("Relation").get("enum")));
        Set<String> fragment = new TreeSet<>(
                texts(schema("org-fragment").get("$defs").get("Relation").get("enum")));

        assertThat(domain).as("the domain has no " + TENSION_SPELLING).doesNotContain(TENSION_SPELLING);
        Set<String> accepted = new TreeSet<>(domain);
        accepted.add(TENSION_SPELLING);
        assertThat(shard)
                .as("the shard schema accepts the domain's relations and exactly the tension spelling")
                .isEqualTo(accepted);
        assertThat(fragment).isEqualTo(shard);
        // The extra word is bound, not merely tolerated: the domain reads it as a tension.
        GraphBuild build = GraphBinding.graph(
                "qa",
                new RawGraph(
                        "x.graph.yaml",
                        RawGraph.Shape.SHARD,
                        List.of(),
                        List.of(new RawEdge(
                                "tactic:a", "tactic:b", TENSION_SPELLING, Optional.empty(), Optional.empty()))));
        assertThat(build.findings()).isEmpty();
        assertThat(build.graph().tensions()).hasSize(1);
        assertThat(build.graph().relationships()).isEmpty();
    }

    // ---- substep delegation kinds -------------------------------------------------------------------

    @Test
    void theSubstepDelegationKindsAreTheScopeTargetsOfAnActionButAnAgentProfile() throws IOException {
        assertDelegationKindsAreTheModel(texts(schema("mission-type")
                .get("$defs")
                .get("SubstepDelegation")
                .get("properties")
                .get("kind")
                .get("enum")));
    }

    @Test
    void theDelegationKindCheckFailsWhenTheSchemaAdmitsAnAgentProfile() throws IOException {
        // Positive control: the enum as it stood before the ruling, with agent_profile.
        List<String> skewed = new ArrayList<>(texts(schema("mission-type")
                .get("$defs")
                .get("SubstepDelegation")
                .get("properties")
                .get("kind")
                .get("enum")));
        skewed.add(NodeKind.AGENT_PROFILE.token());

        assertThatThrownBy(() -> assertDelegationKindsAreTheModel(skewed))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SubstepDelegation.kind enum");
    }

    /**
     * The schema's {@code SubstepDelegation.kind} enum is the kinds a {@code scope} edge from an {@code action} may
     * reach, less {@code agent_profile}: the scope row allows it, but a mission type names roles, never profiles
     * (ADR-0032 decision 1, operator ruling DM-01M7DTN8JWR4G1P35NXGCMS65M). The domain's
     * {@link SubstepDelegation#delegable} answers the same kinds.
     */
    private static void assertDelegationKindsAreTheModel(List<String> schemaEnum) {
        Set<String> model = new TreeSet<>();
        for (EndpointPair pair : RelationType.SCOPE.endpoints()) {
            if (pair.source() == NodeKind.ACTION && pair.target() != NodeKind.AGENT_PROFILE) {
                model.add(pair.target().token());
            }
        }
        assertThat(RelationType.SCOPE.allows(NodeKind.ACTION, NodeKind.AGENT_PROFILE))
                .as("the scope row lets an action reach an agent_profile; the exclusion is the ruling's")
                .isTrue();
        assertThat(new TreeSet<>(schemaEnum))
                .as("the SubstepDelegation.kind enum")
                .isEqualTo(model);
        assertThat(new TreeSet<>(Arrays.stream(ArtifactKind.values())
                        .filter(SubstepDelegation::delegable)
                        .map(ArtifactKind::token)
                        .toList()))
                .as("the kinds SubstepDelegation.delegable answers")
                .isEqualTo(model);
    }

    // ---- the wire codes the load path projects onto ---------------------------------------------------

    @Test
    void everyLoadPathWireTokenIsInTheContractsCodeEnums() throws IOException {
        Path schemas = Path.of(System.getProperty("charter.contractSchemas"));
        Set<String> contract = new LinkedHashSet<>();
        for (String file : List.of("DiagnosticCode.yaml", "CharterRefusalCode.yaml")) {
            Map<?, ?> document = (Map<?, ?>) Yaml.load(Files.readString(schemas.resolve(file), StandardCharsets.UTF_8));
            ((List<?>) document.get("enum")).forEach(v -> contract.add(String.valueOf(v)));
        }
        Set<String> projected = new LinkedHashSet<>();
        for (ReasonCode code : findingCodes()) {
            if (code.severity() == Severity.WARNING) {
                PackFindingCode.diagnostic(code).ifPresent(diagnostic -> projected.add(diagnostic.token()));
            } else if (isOnTheLoadPath(code)) {
                projected.add(PackFindingCode.refusal(code).token());
            }
        }

        assertThat(projected)
                .contains("pack_level_missing", "pack_level_mismatch", "pack_unreadable", "drg_schema_error");
        assertThat(contract).containsAll(projected);
    }

    // ---- the kind taxonomy (R18, FR-003) -----------------------------------------------------------

    @Test
    void theServedKindTaxonomyIsTheDomainsKindsAndRelationsAndTheFileKindTable() {
        assertTaxonomyIsTheModel(new KindTaxonomyReadService().taxonomy());
    }

    @Test
    void theTaxonomyServesTheGlossaryContextAndTheTermAsTheGlossaryWorkModelsThem() {
        // #106: the glossary context stands for the artifact kind glossary under its own URN token; the term is only
        // ever the target of a vocabulary edge, so it has no plural, stands for no artifact kind and is never declared.
        Map<NodeKind, KindFacts> byKind = new java.util.EnumMap<>(NodeKind.class);
        new KindTaxonomyReadService().taxonomy().kinds().forEach(kind -> byKind.put(kind.kind(), kind));

        assertThat(byKind.get(NodeKind.GLOSSARY_CONTEXT))
                .extracting(
                        KindFacts::plural,
                        KindFacts::artifactKind,
                        KindFacts::declarableInGraphs,
                        KindFacts::edgeTarget,
                        KindFacts::listed)
                .containsExactly(Optional.of("glossaries"), Optional.of(ArtifactKind.GLOSSARY), true, true, true);
        assertThat(byKind.get(NodeKind.TERM))
                .extracting(
                        KindFacts::plural,
                        KindFacts::artifactKind,
                        KindFacts::artifact,
                        KindFacts::activatable,
                        KindFacts::declarableInGraphs,
                        KindFacts::edgeTarget,
                        KindFacts::listed)
                .containsExactly(Optional.empty(), Optional.empty(), false, false, false, true, false);
        assertThat(RelationType.VOCABULARY.endpoints())
                .contains(
                        new EndpointPair(NodeKind.GLOSSARY_CONTEXT, NodeKind.TERM),
                        new EndpointPair(NodeKind.GLOSSARY_CONTEXT, NodeKind.GLOSSARY_CONTEXT),
                        new EndpointPair(NodeKind.DIRECTIVE, NodeKind.TERM))
                .noneMatch(pair -> pair.source() == NodeKind.TERM);
    }

    @Test
    void theTaxonomyCheckFailsWhenOneKindsPluralIsSkewed() {
        // Positive control: the same check fails on a taxonomy whose directive row names another plural.
        Taxonomy served = new KindTaxonomyReadService().taxonomy();
        List<KindFacts> skewed = served.kinds().stream()
                .map(kind -> kind.kind() == NodeKind.DIRECTIVE
                        ? new KindFacts(
                                kind.kind(),
                                Optional.of("directivez"),
                                kind.artifactKind(),
                                kind.artifact(),
                                kind.activatable(),
                                kind.declarableInGraphs(),
                                kind.edgeTarget(),
                                kind.listed())
                        : kind)
                .toList();

        assertThatThrownBy(() -> assertTaxonomyIsTheModel(new Taxonomy(skewed, served.relations())))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("plural of directive");
    }

    @Test
    void theTaxonomyCheckFailsWhenARelationLosesAnEndpoint() {
        Taxonomy served = new KindTaxonomyReadService().taxonomy();
        List<RelationFacts> skewed = served.relations().stream()
                .map(relation -> relation.relation().equals("rejects")
                        ? new RelationFacts(
                                relation.relation(),
                                relation.heldAs(),
                                relation.directed(),
                                relation.derived(),
                                relation.endpoints()
                                        .subList(1, relation.endpoints().size()))
                        : relation)
                .toList();

        assertThatThrownBy(() -> assertTaxonomyIsTheModel(new Taxonomy(served.kinds(), skewed)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("endpoints of rejects");
    }

    @Test
    void theTaxonomyCheckFailsWhenARelationsDirectionIsFlipped() {
        // Positive control for the relation flags: requires is directed in the model, and the skewed row says not.
        Taxonomy served = new KindTaxonomyReadService().taxonomy();
        List<RelationFacts> skewed = served.relations().stream()
                .map(relation -> relation.relation().equals("requires")
                        ? new RelationFacts(
                                relation.relation(),
                                relation.heldAs(),
                                !relation.directed(),
                                relation.derived(),
                                relation.endpoints())
                        : relation)
                .toList();

        assertThatThrownBy(() -> assertTaxonomyIsTheModel(new Taxonomy(served.kinds(), skewed)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("whether requires is directed");
    }

    @Test
    void theTaxonomyCheckFailsWhenAKindsListedFactIsFlipped() {
        Taxonomy served = new KindTaxonomyReadService().taxonomy();
        List<KindFacts> skewed = served.kinds().stream()
                .map(kind -> kind.kind() == NodeKind.TEMPLATE
                        ? new KindFacts(
                                kind.kind(),
                                kind.plural(),
                                kind.artifactKind(),
                                kind.artifact(),
                                kind.activatable(),
                                kind.declarableInGraphs(),
                                kind.edgeTarget(),
                                !kind.listed())
                        : kind)
                .toList();

        assertThatThrownBy(() -> assertTaxonomyIsTheModel(new Taxonomy(skewed, served.relations())))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("whether template is listed");
    }

    @Test
    void theTaxonomyCheckFailsWhenAKindsArtifactKindIsSkewed() {
        // Positive control for artifactKind: the tactic row names the directive artifact kind.
        Taxonomy served = new KindTaxonomyReadService().taxonomy();
        List<KindFacts> skewed = served.kinds().stream()
                .map(kind -> kind.kind() == NodeKind.TACTIC
                        ? new KindFacts(
                                kind.kind(),
                                kind.plural(),
                                Optional.of(ArtifactKind.DIRECTIVE),
                                kind.artifact(),
                                kind.activatable(),
                                kind.declarableInGraphs(),
                                kind.edgeTarget(),
                                kind.listed())
                        : kind)
                .toList();

        assertThatThrownBy(() -> assertTaxonomyIsTheModel(new Taxonomy(skewed, served.relations())))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the artifact kind of tactic");
    }

    @Test
    void theTaxonomyCheckFailsWhenAKindsDeclarableFactIsFlipped() {
        Taxonomy served = new KindTaxonomyReadService().taxonomy();
        List<KindFacts> skewed = served.kinds().stream()
                .map(kind -> kind.kind() == NodeKind.GUARD
                        ? new KindFacts(
                                kind.kind(),
                                kind.plural(),
                                kind.artifactKind(),
                                kind.artifact(),
                                kind.activatable(),
                                !kind.declarableInGraphs(),
                                kind.edgeTarget(),
                                kind.listed())
                        : kind)
                .toList();

        assertThatThrownBy(() -> assertTaxonomyIsTheModel(new Taxonomy(skewed, served.relations())))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("whether guard is declarable in graphs");
    }

    @Test
    void theTaxonomyCheckFailsWhenAKindsEdgeTargetFactIsFlipped() {
        Taxonomy served = new KindTaxonomyReadService().taxonomy();
        List<KindFacts> skewed = served.kinds().stream()
                .map(kind -> kind.kind() == NodeKind.ACTION
                        ? new KindFacts(
                                kind.kind(),
                                kind.plural(),
                                kind.artifactKind(),
                                kind.artifact(),
                                kind.activatable(),
                                kind.declarableInGraphs(),
                                !kind.edgeTarget(),
                                kind.listed())
                        : kind)
                .toList();

        assertThatThrownBy(() -> assertTaxonomyIsTheModel(new Taxonomy(skewed, served.relations())))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("whether action is an edge target");
    }

    /**
     * The taxonomy holds every kind of {@code file-kinds.yaml}'s {@code node_kinds} in its order, each with the plural
     * {@code fragment_kinds} names it by (the kernel's plural for the two kinds no fragment names), its artifact kind,
     * artifact and activation facts as {@code artifact_kinds} and {@link ArtifactKind} hold them, whether a graph
     * document declares it ({@code fragment_kinds}) and whether an edge targets it (the shard schema's edge target) and whether the pack artifact list
     * serves it ({@link ArtifactReadService#listed()}); and every {@link RelationType}, then the tension, each held as
     * the model holds it, with the kernel's direction, derivation and endpoints ({@link RelationType}, {@link Tension}).
     */
    private static void assertTaxonomyIsTheModel(Taxonomy taxonomy) {
        PackFileKind table = PackFileKind.bundled();
        assertThat(taxonomy.kinds().stream().map(kind -> kind.kind().token()).toList())
                .as("the kinds, in file-kinds.yaml node_kinds order")
                .containsExactlyElementsOf(table.nodeKinds());
        Map<String, String> pluralOf = new java.util.HashMap<>();
        table.fragmentKinds().forEach((plural, token) -> pluralOf.put(token, plural));
        List<String> artifactKinds = fileKindsArtifactKinds();
        Set<String> edgeTargets = shardEdgeTargets();
        for (KindFacts kind : taxonomy.kinds()) {
            assertKindIsTheModel(kind, pluralOf, artifactKinds, edgeTargets);
        }
        assertRelationsAreTheModel(taxonomy);
    }

    /** One row of the taxonomy against the file-kind table, the shard schema's edge target and the domain. */
    private static void assertKindIsTheModel(
            KindFacts kind, Map<String, String> pluralOf, List<String> artifactKinds, Set<String> edgeTargets) {
        String token = kind.kind().token();
        Optional<ArtifactKind> artifact = kind.kind().artifactKind();
        assertThat(kind.plural())
                .as("the plural of " + token)
                .isEqualTo(Optional.ofNullable(pluralOf.get(token)).or(() -> artifact.map(ArtifactKind::plural)));
        // A node kind stands for the artifact kind of its token, or, when the tokens differ (glossary_context
        // stands for glossary, ADR-0053 decision 13), for the artifact kind of its fragment plural.
        Optional<String> expectedArtifact = artifactKinds.contains(token)
                ? Optional.of(token)
                : Optional.ofNullable(pluralOf.get(token))
                        .flatMap(ArtifactKind::fromPlural)
                        .map(ArtifactKind::token)
                        .filter(artifactKinds::contains);
        assertThat(kind.artifactKind().map(ArtifactKind::token))
                .as("the artifact kind of " + token)
                .isEqualTo(expectedArtifact);
        assertThat(kind.artifact()).as("whether " + token + " is an artifact").isEqualTo(expectedArtifact.isPresent());
        assertThat(kind.activatable())
                .as("whether " + token + " is activatable")
                .isEqualTo(artifact.map(ArtifactKind::activatable).orElse(false));
        assertThat(kind.declarableInGraphs())
                .as("whether " + token + " is declarable in graphs: a fragment names it by a plural")
                .isEqualTo(pluralOf.containsKey(token));
        assertThat(kind.edgeTarget())
                .as("whether " + token + " is an edge target: the shard schema's edge target admits it")
                .isEqualTo(edgeTargets.contains(token));
        assertThat(kind.listed())
                .as("whether " + token + " is listed, so its row links the pack artifact list")
                .isEqualTo(
                        artifact.filter(ArtifactReadService.listed()::contains).isPresent());
    }

    /** The node kinds the shard schema's edge target admits. */
    private static Set<String> shardEdgeTargets() {
        try {
            return alternation(schema("graph-shard")
                    .get("$defs")
                    .get("DRGEdge")
                    .get("properties")
                    .get("target")
                    .get("pattern")
                    .asString());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Every relation of the taxonomy, then the tension, held, directed, derived and connected as the kernel says. */
    private static void assertRelationsAreTheModel(Taxonomy taxonomy) {
        assertThat(taxonomy.relations().stream().map(RelationFacts::relation).toList())
                .as("the relations, in RelationType order, then the tension")
                .containsExactlyElementsOf(Stream.concat(
                                Arrays.stream(RelationType.values()).map(RelationType::token),
                                Stream.of(TENSION_SPELLING))
                        .toList());
        for (RelationFacts relation : taxonomy.relations()) {
            Optional<RelationType> type = RelationType.fromToken(relation.relation());
            String name = relation.relation();
            assertThat(relation.heldAs())
                    .as("how " + name + " is held")
                    .isEqualTo(type.isPresent() ? HeldAs.RELATIONSHIP : HeldAs.TENSION);
            assertThat(relation.directed())
                    .as("whether " + name + " is directed")
                    .isEqualTo(type.map(RelationType::directionality).orElseGet(Tension::directionality)
                            == Directionality.DIRECTED);
            assertThat(relation.derived())
                    .as("whether " + name + " is derived")
                    .isEqualTo(type.map(RelationType::derived).orElseGet(Tension::derived));
            Set<EndpointPair> expected = type.map(RelationType::endpoints).orElseGet(Tension::allowedEndpoints);
            assertThat(relation.endpoints())
                    .as("the endpoints of " + relation.relation())
                    .isNotEmpty()
                    .doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    private static List<String> fileKindsArtifactKinds() {
        try (InputStream in = VocabularyEqualityTest.class.getResourceAsStream("/packs/file-kinds.yaml")) {
            Object table = Yaml.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return ((List<?>) ((Map<?, ?>) table).get("artifact_kinds"))
                    .stream().map(String::valueOf).toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Every code a finding is built with: the domain's codes, then the staging view's. */
    private static List<ReasonCode> findingCodes() {
        List<ReasonCode> codes = new ArrayList<>(PackCodes.CONTENT);
        codes.addAll(List.of(
                PackFindingCode.PACK_LEVEL_MISSING,
                PackFindingCode.PACK_LEVEL_MISMATCH,
                PackFindingCode.ORG_FRAGMENT_MISSING,
                PackFindingCode.PACK_UNREADABLE,
                PackFindingCode.REQUIRES_CORE_INVALID,
                PackFindingCode.PACK_VERSION_INVALID,
                PackFindingCode.DEPENDENCY_RANGE_INVALID,
                PackFindingCode.ARTIFACT_SCHEMA_ERROR,
                PackFindingCode.ASSET_PATH_ESCAPE,
                PackFindingCode.ASSET_MIME_INVALID,
                PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED,
                PackFindingCode.FILE_NOT_HELD));
        return codes;
    }

    /** A blocking code that projects onto a refusal; the validate-only codes do not and throw. */
    private static boolean isOnTheLoadPath(ReasonCode code) {
        try {
            PackFindingCode.refusal(code);
            return true;
        } catch (IllegalStateException validateOnly) {
            return false;
        }
    }
}
