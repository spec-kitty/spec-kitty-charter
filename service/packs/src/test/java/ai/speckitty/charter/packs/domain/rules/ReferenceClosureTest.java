package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetIdentifier;
import ai.speckitty.charter.packs.domain.ConfigSchemas;
import ai.speckitty.charter.packs.domain.Executable;
import ai.speckitty.charter.packs.domain.ExecutableRuntime;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.GuardIdentifier;
import ai.speckitty.charter.packs.domain.GuardImplementation;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.ReferenceFacts;
import ai.speckitty.charter.packs.domain.ReferenceFacts.AssetSubject;
import ai.speckitty.charter.packs.domain.ReferenceFacts.GuardEntryView;
import ai.speckitty.charter.packs.domain.ReferenceFacts.GuardSubject;
import ai.speckitty.charter.packs.domain.ReferenceFacts.LifecycleView;
import ai.speckitty.charter.packs.domain.ReferenceFacts.MissionTypeView;
import ai.speckitty.charter.packs.domain.ReferenceFacts.OutputTemplate;
import ai.speckitty.charter.packs.domain.ReferenceFacts.Subject;
import ai.speckitty.charter.packs.domain.ReferenceFacts.TransitionView;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reference closure (#355) over hand-built subjects: each case closes or names what does not resolve, in the load's
 * words, and the checks cascade from an asset to the guard it backs and the lifecycle that names that guard. The four
 * paths that call it are pinned together by {@code ClosureSymmetryTest}.
 */
@DisplayName("The reference closure decides every closure case, with the cascade (#355)")
class ReferenceClosureTest {

    private static final PackIdentifier PACK = new PackIdentifier("p");

    /** A schema the fake port refuses, and a value it finds does not fit. */
    private static final ConfigValue BAD = ConfigValue.of(java.util.Map.of("type", "text"));

    /** A schema and a value the fake port accepts. */
    private static final ConfigValue OK = ConfigValue.of(java.util.Map.of("type", "object"));

    private static final ConfigSchemas SCHEMAS = new ConfigSchemas() {
        @Override
        public List<String> schemaProblems(ConfigValue schema) {
            return schema.equals(BAD) ? List.of("type at /type") : List.of();
        }

        @Override
        public List<String> valueProblems(ConfigValue schema, Optional<ConfigValue> value) {
            return value.filter(BAD::equals).isPresent() ? List.of("type at /carrier") : List.of();
        }
    };

    private static AssetSubject asset(String id, String path, Optional<String> entry) {
        return new AssetSubject(Asset.builder()
                .id(new AssetIdentifier(id))
                .packId(PACK)
                .mime("text/x-python")
                .path(path)
                .executable(entry.map(e -> new Executable(ExecutableRuntime.PYTHON, Optional.empty(), e, List.of()))
                        .orElse(null))
                .build());
    }

    private static GuardSubject guard(String id, String implementedBy, Optional<ConfigValue> schema) {
        return new GuardSubject(new Guard(
                new GuardIdentifier(id),
                PACK,
                "1.0",
                id,
                Optional.empty(),
                Guard.INTERFACE,
                GuardImplementation.parse(implementedBy),
                schema,
                Optional.empty(),
                Optional.empty()));
    }

    private static LifecycleView lifecycle(GuardEntryView... entries) {
        return new LifecycleView(
                new Urn(NodeKind.LIFECYCLE, "wp"), "wp", List.of(new TransitionView("a -> b", List.of(entries))));
    }

    private static GuardEntryView entry(String guard, Optional<ConfigValue> config) {
        return new GuardEntryView(false, guard, guard, config);
    }

    private static List<String> reasons(List<Subject> candidates) {
        return ReferenceClosure.of(candidates, Set.of("lint.py", "notes.txt"), Set.of("t/spec.md"), SCHEMAS)
                .findings(candidates)
                .stream()
                .map(ReferenceClosure.Finding::reason)
                .toList();
    }

    @Test
    @DisplayName("a pack whose every reference resolves closes")
    void aPackThatClosesHasNoFinding() {
        assertThat(reasons(List.of(
                        asset("lint", "lint.py", Optional.of("lint.py")),
                        guard("g", "asset:lint", Optional.of(OK)),
                        guard("b", "builtin:implementation-ready", Optional.empty()),
                        lifecycle(entry("g", Optional.of(OK)), entry("b", Optional.empty())))))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "an executable entry that is no payload of the pack is refused, and the cascade takes guard and lifecycle")
    void anEntryThatIsNoPayloadCascades() {
        assertThat(reasons(List.of(
                        asset("lint", "lint.py", Optional.of("other.py")),
                        guard("g", "asset:lint", Optional.empty()),
                        lifecycle(entry("g", Optional.empty())))))
                .containsExactly(
                        "asset 'lint' declares executable entry 'other.py', which is not a payload file the pack holds"
                                + " under assets/",
                        "guard 'g' is implemented_by asset:lint, which the pack does not hold",
                        "lifecycle 'wp' transition a -> b names guard 'g', which the pack does not hold");
    }

    @Test
    @DisplayName(
            "a guard's asset must be held and runnable; another pack's asset is refused until the closure resolves it")
    void aGuardsAssetMustBeHeldAndRunnable() {
        assertThat(reasons(List.of(
                        asset("notes", "notes.txt", Optional.empty()),
                        guard("missing", "asset:nope", Optional.empty()),
                        guard("inert", "asset:notes", Optional.empty()),
                        guard("foreign", "qa::asset:lint", Optional.empty()))))
                .containsExactly(
                        "guard 'missing' is implemented_by asset:nope, which the pack does not hold",
                        "guard 'inert' is implemented_by asset:notes, which declares no executable block",
                        "guard 'foreign' is implemented_by qa::asset:lint, a pack-qualified reference; it is refused"
                                + " until the pack dependency closure resolves it");
    }

    @Test
    @DisplayName("a config_schema that does not compile refuses the guard, and the lifecycle that names it")
    void anInvalidSchemaRefusesTheGuard() {
        assertThat(reasons(List.of(
                        guard("g", "builtin:implementation-ready", Optional.of(BAD)),
                        lifecycle(entry("g", Optional.empty())))))
                .containsExactly(
                        "guard 'g': its config_schema is not a valid JSON Schema: type at /type",
                        "lifecycle 'wp' transition a -> b names guard 'g', which the pack does not hold");
    }

    @Test
    @DisplayName("a guard entry's config must fit the guard's schema, and needs a schema to be checked against")
    void anEntrysConfigIsChecked() {
        GuardSubject schemed = guard("s", "builtin:implementation-ready", Optional.of(OK));
        GuardSubject plain = guard("p", "builtin:implementation-ready", Optional.empty());

        assertThat(reasons(List.of(schemed, lifecycle(entry("s", Optional.of(BAD))))))
                .containsExactly("lifecycle 'wp' transition a -> b: the config of guard 's' does not fit its"
                        + " config_schema: type at /carrier");
        assertThat(reasons(List.of(plain, lifecycle(entry("p", Optional.of(OK))))))
                .containsExactly("lifecycle 'wp' transition a -> b: guard 'p' declares no config_schema, so its config"
                        + " cannot be checked");
    }

    @Test
    @DisplayName("a guard entry naming another pack's guard is refused until the closure resolves it")
    void aCrossPackEntryIsRefused() {
        LifecycleView foreign = new LifecycleView(
                new Urn(NodeKind.LIFECYCLE, "wp"),
                "wp",
                List.of(new TransitionView(
                        "a -> b", List.of(new GuardEntryView(true, "other", "qa::guard:other", Optional.empty())))));

        assertThat(reasons(List.of(foreign)))
                .containsExactly("lifecycle 'wp' transition a -> b names guard qa::guard:other, a pack-qualified"
                        + " reference; it is refused until the pack dependency closure resolves it");
    }

    @Test
    @DisplayName("among candidates of one id, the first that closes is the one a reference names")
    void theFirstThatClosesIsNamed() {
        assertThat(reasons(List.of(
                        asset("lint", "lint.py", Optional.of("gone.py")),
                        asset("lint", "lint.py", Optional.of("lint.py")),
                        guard("g", "asset:lint", Optional.empty()))))
                .containsExactly("asset 'lint' declares executable entry 'gone.py', which is not a payload file the"
                        + " pack holds under assets/");
    }

    @Test
    @DisplayName("a mission type's output template is one of its own; the write wording names the type")
    void aMissionTypesTemplateIsItsOwn() {
        MissionTypeView type = new MissionTypeView(
                new Urn(NodeKind.MISSION_TYPE, "t"),
                "t",
                List.of(
                        new OutputTemplate(Optional.empty(), "templates/spec.md", "t/spec.md"),
                        new OutputTemplate(Optional.of("plan"), "templates/plan.md", "t/plan.md")));

        List<ReferenceClosure.Finding> findings = ReferenceClosure.of(
                        List.of(type), Set.of(), Set.of("t/spec.md"), SCHEMAS)
                .findings(List.of(type));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.reason())
                    .isEqualTo("templates/plan.md (named by step 'plan'): is not a template of the type");
            assertThat(finding.refusal())
                    .isEqualTo("mission_type 't' names templates/plan.md (named by step 'plan'): is not a template of"
                            + " the type");
            assertThat(finding.urn()).isEqualTo(new Urn(NodeKind.MISSION_TYPE, "t"));
        });
    }

    @Test
    @DisplayName(
            "the PACK ruleset states each finding at the candidate's file, and nothing for facts with no candidate")
    void theRuleStatesEachFindingAtItsFile() {
        ReferenceFacts facts = new ReferenceFacts(
                List.of(
                        new ReferenceFacts.Placed("guards/g.guard.yaml", guard("g", "asset:nope", Optional.empty())),
                        new ReferenceFacts.Placed(
                                "guards/b.guard.yaml", guard("b", "builtin:implementation-ready", Optional.empty()))),
                Set.of(),
                Set.of(),
                SCHEMAS);

        List<Reason> found = ReferenceRules.stated(facts);

        assertThat(found).singleElement().satisfies(reason -> {
            assertThat(reason.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
            assertThat(reason.location().file()).isEqualTo("guards/g.guard.yaml");
            assertThat(reason.detail())
                    .isEqualTo("guard 'g' is implemented_by asset:nope, which the pack does not hold");
        });
        assertThat(ReferenceRules.stated(ReferenceFacts.NONE)).isEmpty();
    }
}
