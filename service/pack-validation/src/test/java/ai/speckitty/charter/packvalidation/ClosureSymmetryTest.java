package ai.speckitty.charter.packvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.ConfigValue;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.missions.domain.GuardEntry;
import ai.speckitty.charter.missions.domain.GuardReference;
import ai.speckitty.charter.missions.domain.Lifecycle;
import ai.speckitty.charter.missions.domain.LifecycleState;
import ai.speckitty.charter.missions.domain.LifecycleTransition;
import ai.speckitty.charter.missions.domain.TransitionSource;
import ai.speckitty.charter.packs.application.PackRemovalStore;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.Executable;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.GuardImplementation;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The standing symmetry of the reference closure (#355, operator rulings of 2026-11-17): one table of broken packs, and
 * for every row every path that judges a pack gives the same answer, from one rule ({@code ReferenceClosure}).
 *
 * <ul>
 *   <li><b>Validate mode</b> ({@link PackCheck}) reports an {@code artifact_schema_error} error at each file the load
 *       leaves out, with the load's reason, and no other closure finding than the load records.
 *   <li><b>The load</b> ({@link DomainRepositoryService#load}) leaves the artifact out with that violation.
 *   <li><b>Whole-pack registration</b> ({@code PUT /packs}) refuses the pack: its verdict is the read's rationale,
 *       which {@code PackRegistrationService} refuses whenever it fails, and it fails with the same finding.
 *   <li><b>The whole-pack write</b> ({@link WholePackStore#write}) refuses the aggregate with {@code validation_failed}.
 *   <li><b>The per-file write</b> ({@link CommitStorePackWriteStore#commit} or {@code remove}) refuses with
 *       {@code 409 registration_conflict} and the same reason, and writes nothing. A delete of an asset a guard names is
 *       refused. For a domain invariant ({@code Executable}, {@code Lifecycle}) the two write paths cannot be given the
 *       value: it does not build.
 * </ul>
 *
 * <p>Each row first proves its repaired control, the pack unbroken, on every path (DIRECTIVE_041: a row cannot pass
 * because some other rule tripped). The rows run over a copy of charter's built-in example pack (ADR-0070).
 */
@DisplayName(
        "Every path judges the reference closure alike: validate, load, registration, whole-pack and per-file write")
class ClosureSymmetryTest {

    private static final String PACK = ExamplePacks.BUILT_IN;
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);

    private static final String ASSET = "assets/carrier_lint.py.asset.yaml";
    private static final String PAYLOAD = "assets/carrier_lint.py";
    private static final String GUARD = "guards/traceability-ready.guard.yaml";
    private static final String LIFECYCLE = "lifecycles/work-package.lifecycle.yaml";
    private static final String MISSION_TYPE = "missions/software-dev/software-dev.mission-type.yaml";
    private static final String TEMPLATE = "missions/software-dev/templates/plan-template.md";

    private static final String GUARD_LOSES_ASSET =
            "guard 'traceability-ready' is implemented_by asset:carrier-lint, which the pack does not hold";
    private static final String LIFECYCLE_LOSES_GUARD =
            "lifecycle 'work-package' transition in_progress -> for_review names guard 'traceability-ready', which the"
                    + " pack does not hold";

    private static final YamlSerializationStrategy STRATEGY = new YamlSerializationStrategy();

    /** A finding at a file: its reason, matched whole or, for a reason worded by a library, by its start. */
    record Expected(String file, String reason, boolean whole) {
        static Expected exactly(String file, String reason) {
            return new Expected(file, reason, true);
        }

        static Expected startingWith(String file, String reason) {
            return new Expected(file, reason, false);
        }

        boolean matches(Finding finding) {
            return finding.file().equals(file)
                    && (whole
                            ? finding.reason().equals(reason)
                            : finding.reason().startsWith(reason));
        }

        /** Whether validate mode states this finding: the same file, in words that end with the load's (#554). */
        boolean statedBy(Finding finding) {
            return finding.file().equals(file)
                    && (whole
                            ? finding.reason().endsWith(reason)
                            : finding.reason().contains(reason));
        }
    }

    /** A closure finding as one path states it. */
    record Finding(String file, String reason) {
        /**
         * Whether a finding of validate mode or of registration states the same fault as this load finding, as #554
         * defines it ({@code AssemblyRules.sameFault}): the same file, in words that end with the load's. The read
         * words a named file's refusal {@code names <path> ...}, where the load records {@code <path> ...}.
         */
        boolean statedBy(Finding stated) {
            return stated.file().equals(file) && stated.reason().endsWith(reason);
        }
    }

    /** What a write path is asked to do to the aggregate a row breaks. */
    sealed interface Write {}

    /** A per-file write of one artifact: the aggregate after it, and the artifact. */
    record Commit(Function<Pack, CharterArtifact> artifact) implements Write {}

    /** A per-file delete of artifacts. */
    record Remove(List<Urn> artifacts) implements Write {}

    /** A domain invariant: the value the write would carry does not build. */
    record Unbuildable(Function<Pack, ThrowingCallable> build) implements Write {}

    /** A row: a broken tree, the findings the load records, the write that would produce it and its refusal. */
    record Row(String name, TreeEdit edit, List<Expected> expected, Write write, String refusal) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** An edit of the pack tree. */
    @FunctionalInterface
    interface TreeEdit {
        void apply(Path pack) throws IOException;
    }

    static Stream<Row> rows() {
        return Stream.of(
                // row01 to row11: the reference closure; row12 to row17: the Executable and Lifecycle invariants
                row01(),
                row02(),
                row03(),
                row04(),
                row05(),
                row06(),
                row07(),
                row08(),
                row09(),
                row10(),
                row11(),
                row12(),
                row13(),
                row14(),
                row15(),
                row16(),
                row17());
    }

    /** The row: an executable entry that is no payload of the pack, with its cascade. */
    private static Row row01() {
        return new Row(
                "an executable entry that is no payload of the pack, with its cascade",
                edit(ASSET, text -> text.replace("  entry: carrier_lint.py\n", "  entry: other.py\n")),
                List.of(
                        Expected.exactly(
                                ASSET,
                                "asset 'carrier-lint' declares executable entry 'other.py', which is not a"
                                        + " payload file the pack holds under assets/"),
                        Expected.exactly(GUARD, GUARD_LOSES_ASSET),
                        Expected.exactly(LIFECYCLE, LIFECYCLE_LOSES_GUARD)),
                new Commit(pack -> withEntry(asset(pack), "other.py")),
                "asset 'carrier-lint' declares executable entry 'other.py', which is not a payload file the pack"
                        + " holds under assets/");
    }

    /** The row: a guard implemented_by an asset the pack does not hold. */
    private static Row row02() {
        return new Row(
                "a guard implemented_by an asset the pack does not hold",
                edit(GUARD, text -> text.replace("asset:carrier-lint", "asset:missing-lint")),
                List.of(
                        Expected.exactly(
                                GUARD,
                                "guard 'traceability-ready' is implemented_by asset:missing-lint, which the pack"
                                        + " does not hold"),
                        Expected.exactly(LIFECYCLE, LIFECYCLE_LOSES_GUARD)),
                new Commit(pack -> implementedBy(guard(pack), "asset:missing-lint")),
                "guard 'traceability-ready' is implemented_by asset:missing-lint, which the pack does not hold");
    }

    /** The row: a guard implemented_by an asset with no executable block. */
    private static Row row03() {
        return new Row(
                "a guard implemented_by an asset with no executable block",
                edit(GUARD, text -> text.replace("asset:carrier-lint", "asset:logo")),
                List.of(
                        Expected.exactly(
                                GUARD,
                                "guard 'traceability-ready' is implemented_by asset:logo, which declares no"
                                        + " executable block"),
                        Expected.exactly(LIFECYCLE, LIFECYCLE_LOSES_GUARD)),
                new Commit(pack -> implementedBy(guard(pack), "asset:logo")),
                "guard 'traceability-ready' is implemented_by asset:logo, which declares no executable block");
    }

    /** The row: a guard implemented_by another pack's asset. */
    private static Row row04() {
        return new Row(
                "a guard implemented_by another pack's asset",
                edit(GUARD, text -> text.replace("asset:carrier-lint", "qa::asset:qa-lint")),
                List.of(
                        Expected.exactly(
                                GUARD,
                                "guard 'traceability-ready' is implemented_by qa::asset:qa-lint, a"
                                        + " pack-qualified reference; it is refused until the pack dependency"
                                        + " closure resolves it"),
                        Expected.exactly(LIFECYCLE, LIFECYCLE_LOSES_GUARD)),
                new Commit(pack -> implementedBy(guard(pack), "qa::asset:qa-lint")),
                "guard 'traceability-ready' is implemented_by qa::asset:qa-lint, a pack-qualified reference; it is"
                        + " refused until the pack dependency closure resolves it");
    }

    /** The row: a guard whose config_schema is not a valid JSON Schema. */
    private static Row row05() {
        return new Row(
                "a guard whose config_schema is not a valid JSON Schema",
                edit(GUARD, text -> text.replace("      type: string\n", "      type: text\n")),
                List.of(
                        Expected.startingWith(
                                GUARD, "guard 'traceability-ready': its config_schema is not a valid JSON Schema:"),
                        Expected.exactly(LIFECYCLE, LIFECYCLE_LOSES_GUARD)),
                new Commit(pack -> withSchema(guard(pack), ConfigValue.of(Map.of("type", "text")))),
                "guard 'traceability-ready': its config_schema is not a valid JSON Schema:");
    }

    /** The row: a lifecycle naming a guard the pack does not hold. */
    private static Row row06() {
        return new Row(
                "a lifecycle naming a guard the pack does not hold",
                edit(LIFECYCLE, text -> text.replace("  - ref: traceability-ready\n", "  - ref: missing-ready\n")),
                List.of(Expected.exactly(
                        LIFECYCLE,
                        "lifecycle 'work-package' transition in_progress -> for_review names guard"
                                + " 'missing-ready', which the pack does not hold")),
                new Commit(
                        pack -> withEntry(lifecycle(pack), 1, "traceability-ready", "missing-ready", Optional.empty())),
                "lifecycle 'work-package' transition in_progress -> for_review names guard 'missing-ready', which"
                        + " the pack does not hold");
    }

    /** The row: a lifecycle naming another pack's guard. */
    private static Row row07() {
        return new Row(
                "a lifecycle naming another pack's guard",
                edit(LIFECYCLE, text -> text.replace("  - ref: traceability-ready\n", "  - ref: qa::guard:other\n")),
                List.of(Expected.exactly(
                        LIFECYCLE,
                        "lifecycle 'work-package' transition in_progress -> for_review names guard"
                                + " qa::guard:other, a pack-qualified reference; it is refused until the pack"
                                + " dependency closure resolves it")),
                new Commit(pack ->
                        withEntry(lifecycle(pack), 1, "traceability-ready", "qa::guard:other", Optional.empty())),
                "lifecycle 'work-package' transition in_progress -> for_review names guard qa::guard:other, a"
                        + " pack-qualified reference; it is refused until the pack dependency closure resolves"
                        + " it");
    }

    /** The row: a guard entry whose config does not fit the guard's config_schema. */
    private static Row row08() {
        return new Row(
                "a guard entry whose config does not fit the guard's config_schema",
                edit(LIFECYCLE, text -> text.replace("      carrier: qa-carrier.yaml\n", "      carrier: 3\n")),
                List.of(Expected.startingWith(
                        LIFECYCLE,
                        "lifecycle 'work-package' transition in_progress -> for_review: the config of guard"
                                + " 'traceability-ready' does not fit its config_schema:")),
                new Commit(pack -> withEntry(
                        lifecycle(pack),
                        1,
                        "traceability-ready",
                        "traceability-ready",
                        Optional.of(ConfigValue.of(Map.of("carrier", 3))))),
                "lifecycle 'work-package' transition in_progress -> for_review: the config of guard"
                        + " 'traceability-ready' does not fit its config_schema:");
    }

    /** The row: a guard entry with a config, for a guard that declares no config_schema. */
    private static Row row09() {
        return new Row(
                "a guard entry with a config, for a guard that declares no config_schema",
                edit(
                        LIFECYCLE,
                        text -> text.replace(
                                "  - ref: subtasks-ready\n    blocking: true\n",
                                "  - ref: subtasks-ready\n    config:\n      strict: true\n")),
                List.of(Expected.exactly(
                        LIFECYCLE,
                        "lifecycle 'work-package' transition for_review -> done: guard 'subtasks-ready'"
                                + " declares no config_schema, so its config cannot be checked")),
                new Commit(pack -> withEntry(
                        lifecycle(pack),
                        3,
                        "subtasks-ready",
                        "subtasks-ready",
                        Optional.of(ConfigValue.of(Map.of("strict", true))))),
                "lifecycle 'work-package' transition for_review -> done: guard 'subtasks-ready' declares no"
                        + " config_schema, so its config cannot be checked");
    }

    /** The row: a mission type naming a template the pack does not hold (a delete of that template). */
    private static Row row10() {
        return new Row(
                "a mission type naming a template the pack does not hold (a delete of that template)",
                tree -> Files.delete(tree.resolve(TEMPLATE)),
                // The read's own check of a named file states it first, on validate mode and the load alike;
                // the two write
                // paths, which carry no file, refuse it through the closure.
                List.of(Expected.exactly(MISSION_TYPE, "templates/plan-template.md (named by step 'plan'): missing")),
                new Remove(List.of(new Urn(NodeKind.TEMPLATE, "software-dev/plan-template.md"))),
                "mission_type 'software-dev' names templates/plan-template.md (named by step 'plan'): is not a template of the"
                        + " type");
    }

    /** The row: a delete of the asset a guard names is refused, not cascaded. */
    private static Row row11() {
        return new Row(
                "a delete of the asset a guard names is refused, not cascaded",
                tree -> {
                    Files.delete(tree.resolve(ASSET));
                    Files.delete(tree.resolve(PAYLOAD));
                },
                List.of(Expected.exactly(GUARD, GUARD_LOSES_ASSET), Expected.exactly(LIFECYCLE, LIFECYCLE_LOSES_GUARD)),
                new Remove(List.of(new Urn(NodeKind.ASSET, "carrier-lint"))),
                GUARD_LOSES_ASSET);
    }

    /** The row: an executable whose args repeat its entry. */
    private static Row row12() {
        return new Row(
                "an executable whose args repeat its entry",
                edit(ASSET, text -> text.replace("  - --guard\n", "  - --guard\n  - carrier_lint.py\n")),
                List.of(
                        Expected.startingWith(ASSET, ""),
                        Expected.exactly(GUARD, GUARD_LOSES_ASSET),
                        Expected.exactly(LIFECYCLE, LIFECYCLE_LOSES_GUARD)),
                new Unbuildable(pack -> () -> new Executable(
                        asset(pack).executable().orElseThrow().runtime(),
                        Optional.empty(),
                        "carrier_lint.py",
                        List.of("--guard", "carrier_lint.py"))),
                "exactly one argv element");
    }

    /** The row: a lifecycle transition to a state it does not declare. */
    private static Row row13() {
        return lifecycleInvariant(
                "a lifecycle transition to a state it does not declare",
                text -> text.replace("  to: in_progress\n  event: claim\n", "  to: doing\n  event: claim\n"),
                "names only states the lifecycle declares",
                lifecycle -> replaceTransition(
                        lifecycle,
                        0,
                        new LifecycleTransition(
                                new TransitionSource.One("planned"), "doing", Optional.of("claim"), List.of())));
    }

    /** The row: a lifecycle with no terminal state. */
    private static Row row14() {
        return lifecycleInvariant(
                "a lifecycle with no terminal state",
                text -> text.replace("  terminal: true\n", ""),
                "at least one terminal state",
                lifecycle -> rebuild(
                        lifecycle,
                        lifecycle.states().stream()
                                .map(state -> new LifecycleState(
                                        state.id(), state.title(), state.description(), Optional.empty()))
                                .toList(),
                        lifecycle.transitions()));
    }

    /** The row: a lifecycle with a state the initial state cannot reach. */
    private static Row row15() {
        return lifecycleInvariant(
                "a lifecycle with a state the initial state cannot reach",
                text -> text.replace(
                        "- id: blocked\n  title: Blocked\n",
                        "- id: blocked\n  title: Blocked\n- id: limbo\n  title: Limbo\n"),
                "state 'limbo' is not reachable",
                lifecycle -> {
                    List<LifecycleState> states = new ArrayList<>(lifecycle.states());
                    states.add(new LifecycleState("limbo", "Limbo", Optional.empty(), Optional.empty()));
                    return rebuild(lifecycle, states, lifecycle.transitions());
                });
    }

    /** The row: a lifecycle transition out of a terminal state. */
    private static Row row16() {
        return lifecycleInvariant(
                "a lifecycle transition out of a terminal state",
                text -> text + "- from: done\n  to: in_progress\n  event: reopen\n",
                "No lifecycle transition leaves a terminal state",
                lifecycle -> appendTransition(
                        lifecycle,
                        new LifecycleTransition(
                                new TransitionSource.One("done"), "in_progress", Optional.of("reopen"), List.of())));
    }

    /** The row: a lifecycle with two targets for one state and event. */
    private static Row row17() {
        return lifecycleInvariant(
                "a lifecycle with two targets for one state and event",
                text -> text + "- from: planned\n  to: blocked\n  event: claim\n",
                "leads to both 'in_progress' and 'blocked'",
                lifecycle -> appendTransition(
                        lifecycle,
                        new LifecycleTransition(
                                new TransitionSource.One("planned"), "blocked", Optional.of("claim"), List.of())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void everyPathJudgesTheRowAlike(Row row, @TempDir Path host) throws IOException {
        Path control = ExamplePacks.copyOf(PACK, host.resolve("control"));
        Pack held = load(control);
        WholePackStore whole = new WholePackStore(STRATEGY);
        WholePackStore.LoadedPack controlTree = whole.load(control);
        assertTheControlPasses(control, held, whole, controlTree, host);

        Path broken = ExamplePacks.copyOf(PACK, host.resolve("broken"));
        row.edit().apply(broken);
        assertTheReadPathsAgree(row, broken);

        switch (row.write()) {
            case Unbuildable unbuildable ->
                assertThatThrownBy(unbuildable.build().apply(held))
                        .as("the value the write would carry does not build")
                        .isInstanceOf(ConstraintBreachException.class)
                        .hasMessageContaining(row.refusal());
            case Commit commit -> assertTheCommitIsRefused(row, commit, held, whole, controlTree, control, host);
            case Remove remove -> {
                assertWholePackWriteRefuses(whole, controlTree, held.without(remove.artifacts()), host, row.refusal());
                CommitStorePackWriteStore store = commitStore(host.resolve("control"));
                assertPerFileRefusal(
                        () -> store.remove(
                                PACK_ID,
                                PackRemovalStore.Removal.ofArtifacts(
                                        remove.artifacts(), List.of(), List.of(), List.of())),
                        control,
                        row.refusal());
            }
        }
    }

    /** The repaired control: the unbroken pack passes validate mode, the load, registration and the whole-pack write. */
    private static void assertTheControlPasses(
            Path control, Pack held, WholePackStore whole, WholePackStore.LoadedPack controlTree, Path host) {
        assertThat(validated(control)).as("validate mode over the control").isEmpty();
        assertThat(loaded(held)).as("the load of the control").isEmpty();
        assertThat(registered(control).rationale().details().stream()
                        .filter(reason -> PackCodes.ARTIFACT_SCHEMA_ERROR.equals(reason.code())))
                .as("registration's verdict over the control")
                .isEmpty();
        assertThatCode(() ->
                        whole.write(controlTree, host.resolve("control-out").resolve(PACK)))
                .as("the whole-pack write of the control")
                .doesNotThrowAnyException();
    }

    /** Validate mode, the load and registration state the broken tree's closure findings alike. */
    private static void assertTheReadPathsAgree(Row row, Path broken) {
        List<Finding> validated = validated(broken);
        List<Finding> loaded = loaded(load(broken));
        for (Expected expected : row.expected()) {
            assertThat(validated).as("validate mode reports %s", expected).anyMatch(expected::statedBy);
            assertThat(loaded).as("the load records %s", expected).anyMatch(expected::matches);
        }
        assertThat(validated)
                .as("validate mode states exactly the closure findings the load records")
                .hasSameSizeAs(loaded)
                .allSatisfy(stated -> assertThat(loaded).anyMatch(recorded -> recorded.statedBy(stated)));
        assertThat(loaded).allSatisfy(recorded -> assertThat(validated).anyMatch(recorded::statedBy));
        PackReadout registration = registered(broken);
        assertThat(registration.rationale().isFailing())
                .as("whole-pack registration refuses the pack")
                .isTrue();
        List<Finding> registered = findings(registration.rationale().details());
        assertThat(loaded)
                .as("registration's verdict states the load's findings")
                .allSatisfy(recorded -> assertThat(registered).anyMatch(recorded::statedBy));
    }

    /** The two write paths refuse the broken artifact, and the per-file write takes the unbroken one. */
    private static void assertTheCommitIsRefused(
            Row row,
            Commit commit,
            Pack held,
            WholePackStore whole,
            WholePackStore.LoadedPack controlTree,
            Path control,
            Path host)
            throws IOException {
        CharterArtifact artifact = commit.artifact().apply(held);
        Pack after = held.with(artifact);
        assertWholePackWriteRefuses(whole, controlTree, after, host, row.refusal());
        CommitStorePackWriteStore store = commitStore(host.resolve("control"));
        String revision = store.currentRevision(PACK_ID).token();
        assertPerFileRefusal(
                () -> store.commit(PACK_ID, revision, PackWriteStore.Change.artifact(after, artifact)),
                control,
                row.refusal());
        assertThatCode(() ->
                        store.commit(PACK_ID, revision, PackWriteStore.Change.artifact(held, sameAs(held, artifact))))
                .as("the per-file write of the unbroken artifact")
                .doesNotThrowAnyException();
    }

    // ---- the paths -----------------------------------------------------------------------------------------------

    private static PackRoot root(Path pack) {
        return new PackRoot(PACK, Optional.of(ExamplePacks.level(PACK)), pack.toString());
    }

    /** Validate mode's closure findings: every {@code artifact_schema_error}, an error. */
    private static List<Finding> validated(Path pack) {
        List<Reason> findings = new PackCheck(new FileSystemPackInspector())
                .validate(root(pack))
                .findings();
        findings.stream()
                .filter(reason -> PackCodes.ARTIFACT_SCHEMA_ERROR.equals(reason.code()))
                .forEach(reason -> assertThat(
                                reason.isFailing(ai.speckitty.charter.kernel.validation.Severity.VIOLATION))
                        .as("a closure finding is an error")
                        .isTrue());
        return findings(findings);
    }

    private static List<Finding> findings(List<Reason> reasons) {
        return reasons.stream()
                .filter(reason -> PackCodes.ARTIFACT_SCHEMA_ERROR.equals(reason.code()))
                .map(reason -> new Finding(reason.location().file(), unplaced(reason)))
                .toList();
    }

    /**
     * A finding's reason without the file it is placed at: a reader's detail is worded {@code <file>: <reason>}
     * ({@code PackCodes.detail}), and the load records the reason alone, at the file.
     */
    private static String unplaced(Reason reason) {
        String prefix = reason.location().file() + ": ";
        return reason.detail().startsWith(prefix) ? reason.detail().substring(prefix.length()) : reason.detail();
    }

    private static PackReadout registered(Path pack) {
        return new FileSystemPackInspector().read(root(pack));
    }

    private static Pack load(Path pack) {
        return new DomainRepositoryService(new StorageRootLocations(pack.getParent()), STRATEGY).load(PACK_ID);
    }

    /** The load's closure violations: every {@code artifact_schema_error} it records. */
    private static List<Finding> loaded(Pack pack) {
        return pack.violations().stream()
                .filter(violation -> PackCodes.ARTIFACT_SCHEMA_ERROR.equals(violation.kind()))
                .map(violation -> new Finding(violation.file(), violation.reason()))
                .toList();
    }

    private static CommitStorePackWriteStore commitStore(Path packs) {
        StorageRootLocations locations = new StorageRootLocations(packs);
        return new CommitStorePackWriteStore(locations, STRATEGY, new PackCommitStore(locations, STRATEGY));
    }

    private static void assertWholePackWriteRefuses(
            WholePackStore whole, WholePackStore.LoadedPack tree, Pack after, Path host, String refusal) {
        Path target = host.resolve("broken-out").resolve(PACK);
        assertThatThrownBy(() -> whole.write(
                        new WholePackStore.LoadedPack(
                                after, tree.layout(), tree.graphs(), tree.notHeld(), tree.manifest()),
                        target))
                .as("the whole-pack write refuses the aggregate")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED))
                .message()
                .startsWith(refusal);
        assertThat(target).doesNotExist();
    }

    private static void assertPerFileRefusal(ThrowingCallable write, Path pack, String refusal) throws IOException {
        Map<String, String> before = tree(pack);
        assertThatThrownBy(write)
                .as("the per-file write is refused")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT))
                .message()
                .startsWith(refusal);
        assertThat(tree(pack)).as("a refused per-file write changes no file").isEqualTo(before);
    }

    private static Map<String, String> tree(Path pack) throws IOException {
        try (Stream<Path> walk = Files.walk(pack)) {
            return walk.filter(Files::isRegularFile)
                    .collect(Collectors.toMap(file -> pack.relativize(file).toString(), ClosureSymmetryTest::bytesOf));
        }
    }

    private static String bytesOf(Path file) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException | java.security.NoSuchAlgorithmException failed) {
            throw new IllegalStateException(failed);
        }
    }

    // ---- the edits ----------------------------------------------------------------------------------------------

    private static TreeEdit edit(String file, UnaryOperator<String> change) {
        return pack -> {
            Path path = pack.resolve(file);
            String before = Files.readString(path);
            String after = change.apply(before);
            assertThat(after).as("the edit of %s changes it", file).isNotEqualTo(before);
            Files.writeString(path, after);
        };
    }

    private static Row lifecycleInvariant(
            String name, UnaryOperator<String> change, String rule, Function<Lifecycle, Lifecycle> build) {
        return new Row(
                name,
                edit(LIFECYCLE, change),
                List.of(Expected.startingWith(LIFECYCLE, "")),
                new Unbuildable(pack -> () -> build.apply(lifecycle(pack))),
                rule);
    }

    // ---- the domain values -----------------------------------------------------------------------------------------

    private static Asset asset(Pack pack) {
        return pack.assets().stream()
                .filter(asset -> asset.localId().equals("carrier-lint"))
                .findFirst()
                .orElseThrow();
    }

    private static Guard guard(Pack pack) {
        return pack.artifacts(ArtifactKind.GUARD, Guard.class).stream()
                .filter(guard -> guard.localId().equals("traceability-ready"))
                .findFirst()
                .orElseThrow();
    }

    private static Lifecycle lifecycle(Pack pack) {
        return pack.artifacts(ArtifactKind.LIFECYCLE, Lifecycle.class).getFirst();
    }

    /** The artifact the pack holds of the same kind and id, as it was loaded. */
    private static CharterArtifact sameAs(Pack pack, CharterArtifact artifact) {
        return pack.artifacts(artifact.kind()).stream()
                .filter(held -> held.localId().equals(artifact.localId()))
                .findFirst()
                .orElseThrow();
    }

    private static Asset withEntry(Asset asset, String entry) {
        Executable executable = asset.executable().orElseThrow();
        return Asset.builder()
                .id(asset.id())
                .packId(asset.packId())
                .mime(asset.mime())
                .path(asset.path())
                .title(asset.title().orElse(null))
                .executable(new Executable(executable.runtime(), executable.minVersion(), entry, executable.args()))
                .build();
    }

    private static Guard implementedBy(Guard guard, String spelling) {
        return new Guard(
                guard.id(),
                guard.packId(),
                guard.schemaVersion().orElseThrow(),
                guard.title(),
                guard.description(),
                guard.interfaceName(),
                GuardImplementation.parse(spelling),
                guard.configSchema(),
                guard.limits(),
                guard.onIndeterminate());
    }

    private static Guard withSchema(Guard guard, ConfigValue schema) {
        return new Guard(
                guard.id(),
                guard.packId(),
                guard.schemaVersion().orElseThrow(),
                guard.title(),
                guard.description(),
                guard.interfaceName(),
                guard.implementation(),
                Optional.of(schema),
                guard.limits(),
                guard.onIndeterminate());
    }

    /** The lifecycle with one guard entry of one transition replaced: another reference, and a config if given. */
    private static Lifecycle withEntry(
            Lifecycle lifecycle, int transition, String from, String to, Optional<ConfigValue> config) {
        LifecycleTransition old = lifecycle.transitions().get(transition);
        List<GuardEntry> guards = old.guards().stream()
                .map(entry -> entry.ref().spelling().equals(from)
                        ? new GuardEntry(
                                GuardReference.parse(to),
                                config.isPresent() ? config : entry.config(),
                                config.isPresent() ? Optional.empty() : entry.blocking())
                        : entry)
                .toList();
        return replaceTransition(
                lifecycle, transition, new LifecycleTransition(old.from(), old.to(), old.event(), guards));
    }

    private static Lifecycle replaceTransition(Lifecycle lifecycle, int index, LifecycleTransition transition) {
        List<LifecycleTransition> transitions = new ArrayList<>(lifecycle.transitions());
        transitions.set(index, transition);
        return rebuild(lifecycle, lifecycle.states(), transitions);
    }

    private static Lifecycle appendTransition(Lifecycle lifecycle, LifecycleTransition transition) {
        List<LifecycleTransition> transitions = new ArrayList<>(lifecycle.transitions());
        transitions.add(transition);
        return rebuild(lifecycle, lifecycle.states(), transitions);
    }

    private static Lifecycle rebuild(
            Lifecycle lifecycle, List<LifecycleState> states, List<LifecycleTransition> transitions) {
        return new Lifecycle(
                lifecycle.id(),
                lifecycle.packId(),
                lifecycle.schemaVersion().orElseThrow(),
                lifecycle.title(),
                lifecycle.description(),
                lifecycle.initial(),
                states,
                transitions);
    }
}
