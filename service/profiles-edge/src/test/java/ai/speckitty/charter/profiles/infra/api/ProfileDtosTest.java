package ai.speckitty.charter.profiles.infra.api;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.leanProfile;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.urn;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.PackGovernanceEntry;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.application.ProfileRead;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.SelfReviewStep;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * The wire form of a profile read. The oracle of the mapping is {@code contracts/packs/schemas/agent-profile.schema.json}
 * (fold C10): the exhaustive tests walk its properties, and a field the domain type gains that the wire does not map
 * fails the parity test. The mapper is configured as the service configures it ({@link ProfileJson}).
 */
class ProfileDtosTest {

    private static final Path PACK_SCHEMA = Path.of("../../contracts/packs/schemas/agent-profile.schema.json");

    /** The envelope leaves out the kind members it does not carry, as the packs adapter's mix-in makes it. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitAbsentMembers {}

    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new ProfileJson().profileJsonModule())
            .addModule(new SimpleModule("envelope").setMixInAnnotation(Artifact.class, OmitAbsentMembers.class))
            .build();
    private final ProfileDtos dtos = new ProfileDtos("/api/v1");

    private static JsonNode wire(AgentProfile profile) {
        return JSON.valueToTree(ProfileDtos.profile(profile));
    }

    private static <E> Set<String> tokens(E[] values, java.util.function.Function<E, String> token) {
        return Arrays.stream(values).map(token).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> names(JsonNode object) {
        Set<String> names = new LinkedHashSet<>();
        object.propertyNames().forEach(names::add);
        return names;
    }

    // ---- the pack schema is the oracle ---------------------------------------------------------------------------

    private static JsonNode packSchema() throws IOException {
        assertThat(PACK_SCHEMA).as("the pack schema, relative to the module").exists();
        return JSON.readTree(Files.readString(PACK_SCHEMA));
    }

    /** {@code self-review-protocol} and {@code applies_to_languages} on the wire: camel case of a kebab or snake name. */
    static String camel(String name) {
        StringBuilder camel = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '-' || c == '_') {
                upper = true;
            } else {
                camel.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return camel.toString();
    }

    private static JsonNode definitionOf(JsonNode root, JsonNode property) {
        JsonNode ref = property.has("$ref") ? property.get("$ref") : null;
        if (ref == null && property.has("anyOf")) {
            for (JsonNode alternative : property.get("anyOf")) {
                if (alternative.has("$ref")) {
                    ref = alternative.get("$ref");
                }
            }
        }
        if (ref == null && "array".equals(property.path("type").asString(""))) {
            return definitionOf(root, property.path("items"));
        }
        return ref == null ? property : root.at(ref.asString().substring(1));
    }

    /** Every property of the schema node appears on the wire node, and the wire holds no other, down to the leaves. */
    private static void assertSameShape(
            JsonNode root, JsonNode schema, JsonNode wire, String at, List<String> problems) {
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            return;
        }
        Set<String> expected = new TreeSet<>();
        properties.propertyNames().forEach(name -> expected.add(camel(name)));
        Set<String> actual = new TreeSet<>(names(wire));
        if (!expected.equals(actual)) {
            problems.add(at + ": the schema has " + expected + " and the wire " + actual);
            return;
        }
        for (Map.Entry<String, JsonNode> property : properties.properties()) {
            String name = camel(property.getKey());
            if (name.equals("excluding")) {
                continue;
            }
            JsonNode member = wire.get(name);
            JsonNode nested = definitionOf(root, property.getValue());
            if (member.isArray() && !member.isEmpty() && nested.has("properties")) {
                assertSameShape(root, nested, member.get(0), at + "/" + name + "/0", problems);
            } else if (member.isObject()) {
                assertSameShape(root, nested, member, at + "/" + name, problems);
            }
        }
    }

    @Test
    void everyPropertyOfThePackSchemaIsOnTheWireOfAProfileThatSetsEveryFieldAndNothingElseIs() throws IOException {
        JsonNode schema = packSchema();
        List<String> problems = new ArrayList<>();

        assertSameShape(schema, schema, wire(FullProfile.full()), "", problems);

        assertThat(problems).isEmpty();
        assertThat(names(schema.get("properties"))).hasSize(25);
    }

    @Test
    void theWalkOfThePackSchemaReachesTheNestedObjects() throws IOException {
        JsonNode schema = packSchema();
        List<String> problems = new ArrayList<>();
        JsonNode wire = wire(FullProfile.full());
        wire = wire.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) wire.get("specialization")).remove("successDefinition");

        assertSameShape(schema, schema, wire, "", problems);

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("/specialization")
                .contains("successDefinition");
    }

    @Test
    void everyAccessorOfTheDomainProfileHasAMemberOnTheWire() {
        Set<String> notFields = Set.of("hashCode", "toString", "equals", "kind", "packId", "id", "label");
        Map<String, String> renamed = Map.of("localId", "profileId", "selfReviewSteps", "selfReviewProtocol");
        Set<String> accessors = Arrays.stream(AgentProfile.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getParameterCount() == 0)
                .map(java.lang.reflect.Method::getName)
                .filter(name -> !notFields.contains(name))
                .map(name -> renamed.getOrDefault(name, name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(names(wire(FullProfile.full()))).containsAll(accessors);
        assertThat(accessors).as("accessors of the domain profile").hasSize(25);
    }

    // ---- what each field says --------------------------------------------------------------------------------------

    @Test
    void everyFieldOfAProfileThatSetsEveryFieldIsWrittenAsTheProfileHoldsIt() throws IOException {
        JsonNode wire = wire(FullProfile.full());

        assertThat(wire.get("profileId").asString()).isEqualTo("full");
        assertThat(wire.get("name").asString()).isEqualTo("Full");
        assertThat(wire.get("description").asString()).isEqualTo("Every field");
        assertThat(wire.get("schemaVersion").asString()).isEqualTo("2");
        assertThat(wire.get("roles")).hasSize(2);
        assertThat(wire.get("avatarImage").asString()).isEqualTo("avatars/full.png");
        assertThat(wire.get("capabilities").get(1).asString()).isEqualTo("cap-two");
        assertThat(wire.get("routingPriority").asInt()).isEqualTo(80);
        assertThat(wire.get("maxConcurrentTasks").asInt()).isEqualTo(3);
        assertThat(wire.get("model").asString()).isEqualTo("a-model");
        assertThat(wire.get("effort").asString()).isEqualTo("high");
        assertThat(wire.get("purpose").asString()).isEqualTo("Purpose");
        assertThat(wire.get("sentinel").asBoolean()).isTrue();
        assertThat(wire.get("initializationDeclaration").asString()).isEqualTo("I am Full.");
        assertThat(wire.get("specialization").get("secondaryAwareness").asString())
                .isEqualTo("aware");
        assertThat(wire.get("specialization").get("avoidanceBoundary").asString())
                .isEqualTo("avoid");
        assertThat(wire.get("specialization").get("successDefinition").asString())
                .isEqualTo("done");
        assertThat(wire.get("collaboration").get("handoffTo")).hasSize(2);
        assertThat(wire.get("collaboration").get("canonicalVerbs").get(0).asString())
                .isEqualTo("verb-1");
        assertThat(wire.get("modeDefaults").get(1).get("useCase").asString()).isEqualTo("used then");
        assertThat(wire.get("appliesToLanguages").get(1).asString()).isEqualTo("kotlin");
        assertThat(wire.get("directiveReferences").get(1).get("code").asString())
                .isEqualTo("010");
        assertThat(wire.get("tacticReferences").get(0).get("id").asString()).isEqualTo("tactic-1");
        assertThat(wire.get("toolguideReferences").get(0).get("rationale").asString())
                .isEqualTo("because g");
        assertThat(wire.get("styleguideReferences").get(0).get("id").asString()).isEqualTo("styleguide-1");
    }

    @Test
    void writingStyleAndComplexityPreferenceAreArraysInTheirAuthoredOrder() {
        JsonNode context = wire(FullProfile.full()).get("specializationContext");

        assertThat(context.get("writingStyle").isArray()).isTrue();
        assertThat(context.get("writingStyle").get(1).asString()).isEqualTo("terse");
        assertThat(context.get("complexityPreference").isArray()).isTrue();
        assertThat(context.get("complexityPreference").get(0).asString()).isEqualTo("low");
        assertThat(context.get("languages").get(1).asString()).isEqualTo("kotlin");
        assertThat(context.get("frameworks").get(0).asString()).isEqualTo("spring");
        assertThat(context.get("filePatterns").get(0).asString()).isEqualTo("src/**");
        assertThat(context.get("domainKeywords").get(0).asString()).isEqualTo("maven");
    }

    @Test
    void aSelfReviewStepKeepsItsCommandOrWritesNullForAJudgement() {
        JsonNode steps = wire(FullProfile.full()).get("selfReviewProtocol").get("steps");

        assertThat(steps.get(0).get("command").asString()).isEqualTo("mvn compile");
        assertThat(steps.get(1).has("command")).isTrue();
        assertThat(steps.get(1).get("command").isNull()).isTrue();
        assertThat(steps.get(1).get("gate").asString()).isEqualTo("it reads well");
    }

    // ---- null versus empty (fold P5)
    // ---------------------------------------------------------------------------------

    @Test
    void aLeanProfileLeavesOutWhatThePackSchemaDefaultsAndWritesNullWhereThePackSchemaAllowsIt() {
        JsonNode wire = wire(leanProfile("built-in", "lean"));

        assertThat(names(wire))
                .doesNotContain(
                        "description",
                        "schemaVersion",
                        "routingPriority",
                        "maxConcurrentTasks",
                        "sentinel",
                        "initializationDeclaration");
        assertThat(names(wire.get("specialization"))).containsExactly("primaryFocus");
        for (String nullable :
                List.of("avatarImage", "model", "effort", "specializationContext", "selfReviewProtocol", "excluding")) {
            assertThat(wire.has(nullable)).as(nullable + " is written").isTrue();
            assertThat(wire.get(nullable).isNull()).as(nullable + " is null").isTrue();
        }
    }

    @Test
    void aListTheProfileDoesNotDeclareIsAnEmptyArrayNotNullAndNotLeftOut() {
        JsonNode wire = wire(leanProfile("built-in", "lean"));

        for (String list : List.of(
                "capabilities",
                "modeDefaults",
                "appliesToLanguages",
                "directiveReferences",
                "tacticReferences",
                "toolguideReferences",
                "styleguideReferences")) {
            assertThat(wire.get(list).isArray()).as(list + " is an array").isTrue();
            assertThat(wire.get(list)).as(list).isEmpty();
        }
        assertThat(wire.get("collaboration").get("handoffTo").isArray()).isTrue();
        assertThat(wire.get("collaboration")).hasSize(6);
    }

    @Test
    void aSpecializationContextOfSixEmptyListsAndAnAbsentOneAreBothNullBecauseTheDomainDoesNotKeepThemApart() {
        AgentProfile absent = leanProfile("built-in", "a");
        AgentProfile sixEmpty = AgentProfile.builder()
                .id(absent.id())
                .packId(new PackIdentifier("built-in"))
                .name("a")
                .role("implementer")
                .purpose("p")
                .specialization(absent.specialization())
                .specializationContext(
                        new SpecializationContext(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()))
                .build();

        assertThat(wire(absent).get("specializationContext").isNull()).isTrue();
        assertThat(wire(sixEmpty).get("specializationContext").isNull()).isTrue();
    }

    @Test
    void aContextWithOneListIsWrittenWholeWithTheOtherFiveEmpty() {
        AgentProfile lean = leanProfile("built-in", "a");
        AgentProfile oneList = AgentProfile.builder()
                .id(lean.id())
                .packId(new PackIdentifier("built-in"))
                .name("a")
                .role("implementer")
                .purpose("p")
                .specialization(lean.specialization())
                .specializationContext(new SpecializationContext(
                        List.of(), List.of(), List.of(), List.of(), List.of("pragmatic"), List.of()))
                .build();

        JsonNode context = wire(oneList).get("specializationContext");

        assertThat(context.isObject()).isTrue();
        assertThat(context.get("writingStyle").get(0).asString()).isEqualTo("pragmatic");
        assertThat(context.get("languages")).isEmpty();
    }

    @Test
    void aSelfReviewProtocolWithNoStepsAndAnAbsentOneAreBothNull() {
        assertThat(wire(leanProfile("built-in", "a")).get("selfReviewProtocol").isNull())
                .isTrue();
        assertThat(wire(FullProfile.builder().build()).get("selfReviewProtocol").isObject())
                .isTrue();
        AgentProfile oneStep = AgentProfile.builder()
                .id(leanProfile("built-in", "a").id())
                .packId(new PackIdentifier("built-in"))
                .name("a")
                .role("implementer")
                .purpose("p")
                .specialization(leanProfile("built-in", "a").specialization())
                .selfReviewStep(new SelfReviewStep("only", Optional.empty(), "gate"))
                .build();
        assertThat(wire(oneStep).get("selfReviewProtocol").get("steps")).hasSize(1);
    }

    // ---- excluding: both authored forms
    // ---------------------------------------------------------------------------------

    @Test
    void theArrayFormOfExcludingIsWholeFieldsInTheOrderWrittenAndFieldValuesIsNull() {
        AgentProfile profile = FullProfile.builder()
                .excluding(Optional.of(new Exclusion.WholeFields(List.of("tactic-references", "capabilities"))))
                .build();

        JsonNode excluding = wire(profile).get("excluding");

        assertThat(names(excluding)).containsExactly("wholeFields", "fieldValues");
        assertThat(excluding.get("wholeFields").get(0).asString()).isEqualTo("tactic-references");
        assertThat(excluding.get("wholeFields").get(1).asString()).isEqualTo("capabilities");
        assertThat(excluding.get("fieldValues").isNull()).isTrue();
    }

    @Test
    void theObjectFormOfExcludingIsFieldValuesInTheOrderWrittenWithAnEmptyListKeptAndWholeFieldsIsNull() {
        JsonNode excluding = wire(FullProfile.full()).get("excluding");

        assertThat(excluding.get("wholeFields").isNull()).isTrue();
        assertThat(excluding.get("fieldValues")).hasSize(2);
        assertThat(excluding.get("fieldValues").get(0).get("field").asString()).isEqualTo("roles");
        assertThat(excluding.get("fieldValues").get(0).get("values").get(0).asString())
                .isEqualTo("reviewer");
        assertThat(excluding.get("fieldValues").get(1).get("field").asString()).isEqualTo("capabilities");
        assertThat(excluding.get("fieldValues").get(1).get("values").isArray()).isTrue();
        assertThat(excluding.get("fieldValues").get(1).get("values")).isEmpty();
    }

    @Test
    void anEmptyArrayAndAnEmptyObjectOfExcludingAreDifferentOnTheWire() {
        JsonNode emptyArray = wire(FullProfile.builder()
                        .excluding(Optional.of(new Exclusion.WholeFields(List.of())))
                        .build())
                .get("excluding");
        JsonNode emptyObject = wire(FullProfile.builder()
                        .excluding(Optional.of(new Exclusion.FieldValues(new LinkedHashMap<>())))
                        .build())
                .get("excluding");

        assertThat(emptyArray.get("wholeFields").isArray()).isTrue();
        assertThat(emptyArray.get("fieldValues").isNull()).isTrue();
        assertThat(emptyObject.get("wholeFields").isNull()).isTrue();
        assertThat(emptyObject.get("fieldValues").isArray()).isTrue();
    }

    // ---- the envelope, the links and the governance
    // -----------------------------------------------------------------------

    private static GovernanceEntry entry(
            String urn, GovernanceEntry.Step step, String via, int hops, Optional<String> holder, boolean held) {
        Optional<GovernanceEntry.Suggestion> suggestion = step == GovernanceEntry.Step.SUGGESTS
                ? Optional.of(new GovernanceEntry.Suggestion(Optional.of("when x"), Optional.of("because y")))
                : Optional.empty();
        return new GovernanceEntry(urn(urn), step, urn(via), hops, suggestion, holder.map(PackIdentifier::new), held);
    }

    private static ProfileRead read(GovernanceEntry... entries) {
        return new ProfileRead(
                leanProfile("built-in", "P"),
                "sha256:" + "a".repeat(64),
                "sha256:" + "b".repeat(64),
                List.of(entries),
                List.of(ArtifactKind.DIRECTIVE, ArtifactKind.STYLEGUIDE));
    }

    @Test
    void theEnvelopeNamesTheKindTheIdTheRequestedPackAndTheProfileAsItsOnlyKindMember() {
        JsonNode envelope = JSON.valueToTree(dtos.artifact(read(), OptionalInt.empty()));

        assertThat(envelope.get("kind").asString()).isEqualTo("agent_profile");
        assertThat(envelope.get("id").asString()).isEqualTo("P");
        assertThat(envelope.get("packId").asString()).isEqualTo("built-in");
        assertThat(envelope.get("agentProfile").get("profileId").asString()).isEqualTo("P");
        assertThat(envelope.get("governance").isArray()).isTrue();
        assertThat(names(envelope))
                .containsExactlyInAnyOrder("kind", "id", "packId", "agentProfile", "governance", "_links");
    }

    @Test
    void theLinksAreSelfTheCollectionOfTheKindAndTheReferencesRead() {
        Artifact envelope = dtos.artifact(read(), OptionalInt.empty());

        assertThat(envelope.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/built-in/artifacts/agent_profile/P");
        assertThat(envelope.getLinks().get("collection").getHref())
                .isEqualTo("/api/v1/packs/built-in/artifacts?kind=agent_profile");
        assertThat(envelope.getLinks().get("references").getHref())
                .isEqualTo("/api/v1/packs/built-in/artifacts/agent_profile/P/references");
        assertThat(envelope.getLinks()).containsOnlyKeys("self", "collection", "references");
    }

    @Test
    void selfRepeatsTheDepthOnlyWhenTheRequestNamedOneAndTheOtherLinksNeverDo() {
        Artifact withDepth = dtos.artifact(read(), OptionalInt.of(1));
        Artifact zero = dtos.artifact(read(), OptionalInt.of(0));

        assertThat(withDepth.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/built-in/artifacts/agent_profile/P?depth=1");
        assertThat(zero.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/built-in/artifacts/agent_profile/P?depth=0");
        assertThat(withDepth.getLinks().get("references").getHref()).doesNotContain("depth");
    }

    @Test
    void aTrailingSlashOfTheBasePathAndAnIdThatNeedsEncodingAreHandledAsTheActivationsMapperHandlesThem() {
        ProfileRead odd =
                new ProfileRead(leanProfile("a pack", "P 1"), "r", "f", List.of(), List.of(ArtifactKind.DIRECTIVE));

        Artifact envelope = new ProfileDtos("/api/v1/").artifact(odd, OptionalInt.empty());

        assertThat(envelope.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/packs/a%20pack/artifacts/agent_profile/P%201");
    }

    @Test
    void aHeldEntryOfAServedKindLinksTheReadInThePackThatHoldsIt() {
        ProfileRead read = read(entry(
                "directive:D 1", GovernanceEntry.Step.REQUIRES, "agent_profile:P", 1, Optional.of("house"), true));

        PackGovernanceEntry entry =
                dtos.artifact(read, OptionalInt.empty()).getGovernance().getFirst();

        assertThat(entry.getKind()).isEqualTo(PackGovernanceEntry.KindEnum.DIRECTIVE);
        assertThat(entry.getArtifactId()).isEqualTo("D 1");
        assertThat(entry.getUrn()).isEqualTo("directive:D 1");
        assertThat(entry.getPackId()).isEqualTo("house");
        assertThat(entry.getHeld()).isTrue();
        assertThat(entry.getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/house/artifacts/directive/D%201");
    }

    @Test
    void anEntryOfAKindTheGenericRouteDoesNotServeHasEmptyLinks() {
        ProfileRead read = read(entry(
                "agent_profile:Q", GovernanceEntry.Step.LINEAGE, "agent_profile:P", 1, Optional.of("built-in"), true));

        PackGovernanceEntry entry =
                dtos.artifact(read, OptionalInt.empty()).getGovernance().getFirst();

        assertThat(entry.getLinks()).isEmpty();
        assertThat(JSON.valueToTree(entry).get("_links").isObject()).isTrue();
    }

    @Test
    void anEntryNoPackHoldsIsNotHeldWithANullPackIdWrittenAndNoLink() {
        ProfileRead read = read(
                entry("directive:Gone", GovernanceEntry.Step.REQUIRES, "agent_profile:P", 1, Optional.empty(), false));

        JsonNode entry = JSON.valueToTree(
                dtos.artifact(read, OptionalInt.empty()).getGovernance().getFirst());

        assertThat(entry.get("held").asBoolean()).isFalse();
        assertThat(entry.has("packId")).isTrue();
        assertThat(entry.get("packId").isNull()).isTrue();
        assertThat(entry.get("_links")).isEmpty();
    }

    @Test
    void aSuggestsEntryWritesItsSuggestionAndEveryOtherEntryWritesNull() {
        ProfileRead read = read(
                entry(
                        "directive:A",
                        GovernanceEntry.Step.REQUIRES,
                        "agent_profile:P",
                        1,
                        Optional.of("built-in"),
                        true),
                entry(
                        "directive:B",
                        GovernanceEntry.Step.SUGGESTS,
                        "agent_profile:P",
                        1,
                        Optional.of("built-in"),
                        true));

        JsonNode governance =
                JSON.valueToTree(dtos.artifact(read, OptionalInt.empty())).get("governance");

        assertThat(governance.get(0).has("suggestion")).isTrue();
        assertThat(governance.get(0).get("suggestion").isNull()).isTrue();
        assertThat(governance.get(1).get("suggestion").get("when").asString()).isEqualTo("when x");
        assertThat(governance.get(1).get("suggestion").get("reason").asString()).isEqualTo("because y");
    }

    @Test
    void aSuggestionOfAnEdgeThatStatesNeitherIsAnEmptyObjectAndNotNull() {
        GovernanceEntry bare = new GovernanceEntry(
                urn("directive:B"),
                GovernanceEntry.Step.SUGGESTS,
                urn("agent_profile:P"),
                1,
                Optional.of(new GovernanceEntry.Suggestion(Optional.empty(), Optional.empty())),
                Optional.of(new PackIdentifier("built-in")),
                true);

        JsonNode suggestion = JSON.valueToTree(dtos.artifact(read(bare), OptionalInt.empty()))
                .get("governance")
                .get(0)
                .get("suggestion");

        assertThat(suggestion.isObject()).isTrue();
        assertThat(suggestion.size()).isZero();
    }

    /**
     * The walk lists a URN once, at its earliest step, so a URN a dangling edge lists early keeps {@code held} false even
     * when a later step reaches it through a held edge, and the entries below it name it as their {@code via}. The wire
     * says exactly that and does not hide the entry: it is listed with held false and a null packId, and its children
     * point at it.
     */
    @Test
    void anEntryNotHeldCanBeTheViaOfTheEntriesBelowIt() {
        ProfileRead read = read(
                entry("directive:Early", GovernanceEntry.Step.REQUIRES, "agent_profile:P", 1, Optional.empty(), false),
                entry(
                        "directive:Late",
                        GovernanceEntry.Step.VOCABULARY,
                        "directive:Early",
                        2,
                        Optional.of("built-in"),
                        true));

        JsonNode governance =
                JSON.valueToTree(dtos.artifact(read, OptionalInt.empty())).get("governance");

        assertThat(governance.get(0).get("held").asBoolean()).isFalse();
        assertThat(governance.get(1).get("via").asString()).isEqualTo("directive:Early");
        assertThat(governance.get(1).get("hops").asInt()).isEqualTo(2);
        assertThat(governance.get(1).get("held").asBoolean()).isTrue();
    }

    @Test
    void eachStepAndRelationOfTheDomainIsAWireTokenAndEachWireTokenIsOneOfTheDomain() {
        Set<String> steps = Arrays.stream(GovernanceEntry.Step.values())
                .map(GovernanceEntry.Step::token)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> relations = Arrays.stream(GovernanceEntry.Step.values())
                .map(step -> step.relation().token())
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(tokens(PackGovernanceEntry.StepEnum.values(), PackGovernanceEntry.StepEnum::getValue))
                .isEqualTo(steps);
        assertThat(tokens(PackGovernanceEntry.RelationEnum.values(), PackGovernanceEntry.RelationEnum::getValue))
                .isEqualTo(relations);
    }

    @Test
    void theKindsOfAnEntryAreTheNodeKindsTheContractAuthors() {
        Set<String> authored = Arrays.stream(NodeKind.values())
                .filter(NodeKind::authoredInGraphs)
                .map(NodeKind::token)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(tokens(PackGovernanceEntry.KindEnum.values(), PackGovernanceEntry.KindEnum::getValue))
                .isEqualTo(authored);
    }
}
