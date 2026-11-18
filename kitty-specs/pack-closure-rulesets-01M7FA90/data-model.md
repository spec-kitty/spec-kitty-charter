# Data model: ADR-0075 slice 5

All types are in `ai.speckitty.charter.packs.domain` unless noted. Kernel types are in
`ai.speckitty.charter.kernel.validation`.

## Placement (S1 to S3 and S6, 2026-11-16)

| Layer | Package | Holds |
| --- | --- | --- |
| Core | `packs.domain`, `packs.domain.rules` | `PackContents`, `PackCodes`, the content rules, `PackRulesets.content()` and `closure()`, the plain functions |
| | `packs.domain` (S6) | the `PackReading` port and its value types `PackReadout`, `NotHeldFile` and `ClassifiedFile` |
| Storage edge | `packs.infra.storage` | the one reader (`FileSystemPackInspector`, implementing `PackReading`), `PackAssembly`, `PackManifestDerivation`, `CompositionFactsBuilder`, the compose read |
| | `packs.infra.storage.rules` | `TreeRead`, `TreeCensus`, `StorageRulesets.holding()`, `registration()`, `manifestMembership` and `verdict`, `StorageCodes`, the storage rules |
| API edge | `packs.application` | `PackRegistrationService`, `PackRefusals` (the existing package-private class, Q5) |
| Validation edge | `pack-validation` (`packvalidation`) | `PackCheck`, `PackVerdicts`, `Placement`, the placement rules and `PlacementCodes` (Q7); the round trip (#578); the pack set and the CLI later (#478) |

The core knows no `Path`, no bytes, and no YAML or JSON-Schema vocabulary. The storage edge and the validation edge
name the core. `packs.application` and `pack-validation` name `packs.domain` and the kernel only (C-003, S6): they reach
the storage checks through `PackReading`, and nothing outside `packs.infra.storage` and `deployable` names
`packs.infra.storage.rules`. No ArchUnit rule is widened.

## PackCodes and StorageCodes (new, replace PackFindingCode and the string kinds)

Kernel `ReasonCode` constants. Each has a wire token and a severity (ruling 4). Today's tokens are unchanged. **Each
token is built exactly once**: in `PackCodes` when a core rule emits it (the storage edge then names the `PackCodes`
constant); in `PlacementCodes` when only `pack-validation`'s placement rules emit it (Q7); otherwise in `StorageCodes`.

**`PackCodes` (`packs.domain`)**

| Group | Codes (wire token) | Severity |
| --- | --- | --- |
| Graph | `drg_schema_error`, `relationship_endpoint_not_allowed`, `overrides_source_not_activatable` | VIOLATION |
| Artifacts and assets | `duplicate_step_id`, `duplicate_artifact_id`, `asset` (the escape; the reader names it for a missing payload) | VIOLATION |
| Mission type (F) | `mission_step_unlinked`, `substep_delegation_unresolved`, `substep_delegation_unscoped` | VIOLATION |
| Fragments and slots | `prompt_fragment_invalid`, `prompt_slot_malformed`, `prompt_slot_not_empty` | VIOLATION |
| | `prompt_slot_not_empty` (unversioned descriptor) | WARNING |
| Closure (`closure()`) | the 8 `RefusalCode`s of the resolver's cycle, dependency, parent, level and conflict kinds | VIOLATION |

**`StorageCodes` (`packs.infra.storage.rules`)**

| Group | Codes (wire token) | Severity |
| --- | --- | --- |
| Read faults (the one reader) | `pack_unreadable`, `unreadable`, `artifact_schema_error`, `retired`, `companion` | VIOLATION |
| Prechecks (S2, the one reader) | `requires_core_invalid`, `pack_version_invalid`, `dependency_range_invalid`, `asset_mime_invalid` | VIOLATION |
| Holding (`holding()`) | `reserved_shard`, `manifest_membership` | VIOLATION |
| | `file_not_held` (excluded **or unmodelled**, Q1; an unmodelled file's reason is `no pack file kind models this file`) | WARNING |

**`PlacementCodes` (`pack-validation`, `packvalidation.placement`, Q7)**

| Group | Codes (wire token) | Severity |
| --- | --- | --- |
| Placement (the only module that emits them, S2) | `pack_level_missing` | WARNING |
| | `pack_level_mismatch`, `org_fragment_missing` | VIOLATION |

Until WP09 builds `PlacementCodes`, today's `PackPolicy` keeps emitting these tokens through `PackFindingCode`, WP04's
thin view (staging inside the single pull request, R4 b; WP13 deletes it).

Gone: `unmodelled` (Q1), the `invalid` fallback, `domain`, `reference` and `schema` as catch-alls (R2).

## The one reader (S1, FR-016)

`FileSystemPackInspector` (ruling 1 keeps it) is the one reader of a pack tree. One walk produces one **`TreeRead`**
(`packs.infra.storage.rules`), the one input type the storage rules judge, through a package-private method
(`TreeRead readTree(PackRoot root)`). The reader implements the domain port **`PackReading`** (S6) over that walk; a
composition root supplies it: `deployable` to registration (and the same bean to the compose read, which runs
only its store half, `StoreRead`, inside the storage edge; S7), and `PackCheck`'s callers (the tests now, pack-check's main in #478) to the
validation edge. The `TreeRead`:

- **`PackContents`** (`packs.domain`): the storage-agnostic facts: the descriptor, the bound graphs (through
  `GraphBinding`), the artifacts, steps, assets, fragments, prompt slots and glossary terms, and every other input
  `PackAssembly` needs to build the aggregate (the per-artifact digest among them), as domain values. A file the read
  refused yields no fact, and the bound graphs say whether every graph was read, so `content()` skips what the read
  refused without seeing the fault.
- **The read faults**: kernel `Reason`s, each with a kernel `ReasonCode` (the constant built in `PackCodes` or
  `StorageCodes`), the file as its location and the reader's wording. The parse and schema prechecks, the S2 format
  prechecks (descriptor version format, asset mime format), the retired files, value-object breaches and reference
  faults are read faults. The reader owns the schema wording.
- **`TreeCensus`** (`packs.infra.storage.rules`): every regular file of the tree, with its classification
  (`ClassifiedFile`), held or not held. A not-held file is a domain `NotHeldFile` with an explicit kind (`EXCLUDED`,
  `UNMODELLED` or `UNNAMED`, never a reason compare) and its reason; a retired file is not on the not-held list (it is a read
  fault). It also holds the manifest facts (present, regular, the listed set, and the **derived set**, which the reader
  computes through `PackManifestDerivation` while it builds the census) and the reserved-shard stamp. The storage rules
  read the derived set from the census and never call `PackManifestDerivation` (`STORAGE_RULES_ARE_FRAMEWORK_FREE`). It is the one owner of "held": the storage rules, the whole-pack census gate and, through the
  readout, the store's `removeNotHeld` and validate mode all read it.
- **`UNNAMED` (open case 9, WP05).** A file a rule maps that the pack does not name is not held: a `policy.yaml` the
  descriptor's `mutability_policy` does not name, and a companion no artifact names. Both surfaces warn
  `file_not_held` and judge nothing in it; registration removes it, so the stored tree never carries bytes outside the
  revision digest. An orphan prompt fragment is not `UNNAMED`: it keeps `prompt_fragment_invalid`. No golden row
  changes.

The store's own DSO read (`FilesystemPackStore.PackRead` and the bindings) is the walk's internal half: it is mapped
into `PackContents` during the walk, and no package-private assembly input travels beside `TreeRead`, because the
registration path (`packs.application`) can hand back only domain types (S6).

### The port (S6)

```java
// packs.domain
public interface PackReading {
    /** Reads one pack root once; never throws on file content, only on I/O failure. */
    PackReadout read(PackRoot root);
}
public record PackReadout(
        PackContents contents,
        List<ClassifiedFile> files,      // every regular file, classified: what PackVerdict shows
        List<NotHeldFile> notHeld,       // path-sorted: what registration removes
        Rationale rationale) {}          // kernel: StorageRulesets.verdict(read)
public record NotHeldFile(String path, Kind kind, String reason) {   // path is pack-relative
    public enum Kind { EXCLUDED, UNMODELLED, UNNAMED }   // UNNAMED: open case 9 (WP05)
}
public record ClassifiedFile(String file, Optional<String> kind, Optional<String> exclusionClass) {}   // moved out of PackContents

// packs.infra.storage.FileSystemPackInspector implements PackReading
TreeRead readTree(PackRoot root);                                   // package-private: STORAGE_RETURNS_DOMAIN holds
public PackReadout read(PackRoot root) {
    TreeRead tree = readTree(root);
    return new PackReadout(tree.contents(), tree.census().files(), tree.census().notHeld(), StorageRulesets.verdict(tree));
}
```

`PackReadout` is named for what the port returns, not `PackRead`, because `FilesystemPackStore.PackRead` already names
the walk's internal half. The port names only `packs.domain`, the kernel and `java`
(`PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES`, `PACKS_DOMAIN_IS_PURE`). It replaces the domain `PackInspector` port and its
`Scope`: `readTree` takes no scope and reads the whole tree, and the compose read runs its store half (`StoreRead`,
S7) inside storage, so no scope crosses the port. `PackInspector` keeps its consumers (`PackValidation`, `PackLoad`) until WP09, which moves them onto
`PackReading` and deletes the port and its `Scope` (N6).
There is one domain port of the read, and no `TreeReader` port.

`PackAssembly` builds the aggregate from a `PackContents` and judges nothing; its `judged()` withholding stays as
aggregate shaping. Registration reaches it through the store's `Loader` with the readout it judged; the compose read
calls it inside storage. `CompositionFactsBuilder` builds `PackUnderResolution` from the same read, once; `DomainRepositoryService`
delegates to it (P7). The registration path follows no symlinks, and a root that cannot be read answers today's
registration shape (F4).

## The compositions (S1 to S3, replace the `Surface` enum)

```java
// packs.domain.rules (PackRulesets)
public static ModularRuleset<PackContents> content();                // core consistency
public static ModularRuleset<List<PackUnderResolution>> closure();   // CLOSURE, collect-all

// packs.infra.storage.rules (named by nothing outside packs.infra.storage and deployable, S6)
public record TreeRead(PackContents contents, TreeCensus census, List<Reason> readFaults) {}
// StorageRulesets
public static ModularRuleset<TreeRead> holding();                    // not held, reserved shard, manifest a regular file
public static ModularRuleset<TreeRead> registration() {
    return holding().must(PackRulesets.content().on(TreeRead::contents));   // Rule<PackContents>.on -> Rule<TreeRead>
}
public static List<Reason> manifestMembership(TreeRead read);       // plain function, the gated check
public static Rationale verdict(TreeRead read) {                    // what PackReading returns
    Rationale judged = new Rationale(read.readFaults()).and(registration().check(read));
    Rationale gated = judged.isPassing() ? judged.and(new Rationale(manifestMembership(read))) : judged;
    return inRegistrationOrder(gated);                               // registration's wire order (WP07)
}

// pack-validation, packvalidation.placement
public record Placement(PackReadout read, Optional<PackLevel> configuredLevel /* and the org facts */) {}
// PackCheck, constructed with the PackReading its caller supplies
static ModularRuleset<Placement> placement() {
    return ModularRuleset.<Placement>emptyRules()
            .must(LevelRules.RULES)                                  // Rule<Placement>
            .must(OrgFragmentRules.RULES);                           // Rule<Placement>
}
public PackVerdict validate(PackRoot root) {
    PackReadout read = reading.read(root);
    Rationale all = read.rationale().and(placement().check(new Placement(read, root.configuredLevel())));
    return PackVerdicts.of(read.files(), all);
}
```

Each line type-checks against the kernel as it is: `ModularRuleset<T>.must(Rule<T>)` takes a rule over the same
subject; `Rule<T>.on(Function<? super S, ? extends T>)` lifts a rule onto a larger subject `S` and returns a `Rule<S>`,
not a `ModularRuleset<S>`, so a lifted rule is always the argument of `must`, never its receiver; `Rule<T>.check(T)`
returns a `Rationale`, and `Rationale.and(Rationale)` returns "this rationale followed by another" without changing
either. The kernel needs no change.

**Merging the two results (S6).** A kernel `Rationale` is not a ruleset, so `must` cannot extend it. The kernel's own
`Rationale.and` is the union: the readout's reasons, then placement's, in that order, and `isPassing()` over the
joined list fails when either part has a `VIOLATION`. `PackVerdicts` re-sorts with `Precedence.comparator(PlacementCodes.ALL)`, so the order of
the join does not reach the wire.

**The manifest gate is a plain function, not a member of a composition.** `must` is collect-all, and the kernel has no
combinator that runs a rule only when the rules before it passed (`Rule.when` takes a predicate over the subject, so
it would re-judge). `StorageRulesets.verdict` calls `manifestMembership(read)` only when the read faults hold no
`VIOLATION` and `registration().check(read)` is passing (`Rationale.isPassing()`), as `WholePackStore.loadStaged` does
today. Validate mode keeps that choice by construction: the gate runs inside the readout's `Rationale`, before
placement is checked, so placement's reasons (their codes are `PlacementCodes` constants) never gate it, and
registration's faults stay a subset of validate mode's.

There is no mode flag. The core's rules are plain rulesets with no judging port; S6 adds one reading port
(`PackReading`) whose readout carries the storage edge's verdict, composed in storage from the plain rulesets.

| Surface | Where | Runs |
| --- | --- | --- |
| Compose read | `DomainRepositoryService` (storage edge) | `StoreRead`, the store half of the one reader over the same one walk (S7): its assembly facts and faults (`PackContents.Assembly`), no `content()`; first failure as `pack_unreadable`, endpoint prefix kept |
| Registration | `PackRegistrationService` (API edge) | `PackReading.read` → the readout's `Rationale` (read faults + `registration()` + the manifest gate) → `PackRefusals` |
| Validate mode | `PackCheck` (validation edge) | `PackReading.read` → `rationale().and(placement().check(...))` → `PackVerdicts` |
| pack-check (#478) | `pack-validation` | `PackCheck` per pack, `closure()` over the set, the round trip when asked |

`content()` holds: graph and endpoint, mission type, step enhancement and orphan fragments, prompt slot, glossary, the
asset escape and the repeated asset id (S2). The placement rules are `pack-validation`'s `LevelRules` and
`OrgFragmentRules`, over `Placement`.

**The compose read's mapping (S4, S5, S7).** The compose read runs only the store half of the one reader (S7):
`StoreRead`, the same implementation `readTree` runs over the same one walk, with no second DSO reader. It answers from
`StoreRead`'s assembly facts and faults (`PackContents.Assembly`) as today, and runs no `content()` over the
inspector's facts. The store half never produces S2's prechecks (`requires_core_invalid`, `pack_version_invalid`,
`dependency_range_invalid`, `asset_mime_invalid`), so S4 holds by construction and no filter is built. A repeated
artifact id answers `pack_unreadable` with a detail instead of a 500 (S5), landed in WP04 through the store half.
The full one read cost the compose read 3.5 to 4.8 times today's (WP05), because every file is parsed twice.
Single-parse unification is later work, outside this Mission.
The compose read ⊂ registration ⊂ validate mode by construction, so the forward subset holds; the subset tests still
run. Apart from S2's gains (the repeated asset id, the descriptor version format, the asset mime format), registration
and the compose read report the faults they report today (R1's fault sets).

## Composition facts (F5, P7)

`CompositionFactsBuilder`, Spring-free in `packs.infra.storage`, builds `PackUnderResolution` from the one read: the
descriptor, the level, the defined and changed artifacts, and the held terms. `DomainRepositoryService` delegates to
it, so there is one derivation. `closure()` reads these, never the closure walk (#556).

## R2 code table (filled in WP01)

The table lives in `docs/development/notes/pack-code-unification.md`; its open cases 1 to 8 bind WP02, WP04, WP05,
WP06, WP11 and WP12 (the pointers change with the 2026-11-16 replan). S2 changes rows 14, 15 and 38: registration
gains them.

One row per fault: today's validate code and wording, today's registration code, pointer and wording, and the one code
and wording kept. The rule is that the specific code wins over a catch-all. The catch-alls are `domain`, `reference`,
`invalid`, the aggregate `validation_failed`, the refusal at pointer `pack`, and `schema` reported once per violation.
Where registration already answers a specific code, validate mode takes it. Each changed row names its `### Breaking`
fragment.

## Mappers (each at its edge)

| Mapper | Where | Input | Output | Order |
| --- | --- | --- | --- | --- |
| `PackRefusals` | `packs.application`, the existing package-private class beside today's `packRefusal` read mapping (Q5) | the readout's `Rationale` | `List<RefusalViolation>`, plus the diagnostics: the load's warnings in today's order, then one path-sorted list of files not held | as given: `StorageRulesets.verdict` returns registration's order (graph faults by path, then the read and rule faults, the orphans by path, the reserved shard, then manifest membership; warnings in today's order, then the not-held files by path) |
| `PackVerdicts` | `pack-validation` | the readout's classified files + the joined `Rationale` | `PackVerdict(List<ClassifiedFile>, List<Reason>)` | `Precedence.comparator(PlacementCodes.ALL)` (group, path, place) |

Under R2 the mappers differ only in shape and order. Both read `failures()` only. Neither names a storage type
(`StorageCodes` among them, S6): `PackRefusals` keeps the order it is given, and `PackVerdicts` hands `Precedence` the
`PlacementCodes` constants as the group it tests first, never comparing `value()`.

**`Precedence` and `PackVerdict` on kernel types (N2, WP09).** `Precedence` lives in `packs.domain.rules` (`:packs`),
which may never name `pack-validation` (C-003), so the pack-wide group is injected:

```java
// packs.domain.rules.Precedence: kernel and java types only
public static Comparator<Reason> comparator(Set<ReasonCode> firstGroup);
//   group: a code in firstGroup -> 4 (tested first, as PACK_WIDE is today); file pack.yaml -> 1;
//          drg/fragment.yaml -> 3; a root *.graph.yaml -> 2; any other file -> 5
//   then comparePaths(location().file()), then comparePlaces(file, the pointer's segments)
public static int group(ReasonCode code, String file, Set<ReasonCode> firstGroup); // today's PackPolicy.group
public static int comparePaths(String a, String b);                                // unchanged
public static int comparePlaces(String file, List<String> a, List<String> b);      // unchanged

// packs.domain
public record PackVerdict(List<ClassifiedFile> files, List<Reason> findings) {}    // was List<PackFinding>

// pack-validation, PackVerdicts
PackVerdict of(List<ClassifiedFile> files, Rationale all) {
    List<Reason> findings = new ArrayList<>(all.failures());
    findings.sort(Precedence.comparator(PlacementCodes.ALL));                    // ALL: the three placement codes
    return new PackVerdict(files, findings);
}
```

The grouping logic is today's `PackPolicy.group`, unchanged; only its first group is a parameter. The comparator
reads a `Reason`'s place as the segments of its `Location` pointer (RFC 6901, decoded), the inverse of the
place-to-pointer mapping the rules use, so the place order is today's. The validate golden pins it (NFR-002).

## Invariants

- No core rule reads a file or names a storage type. `packs.domain.rules` reads only `PackContents` and
  `PackUnderResolution` (`PACK_RULES_READ_NO_FILE`); `packs.infra.storage.rules` reads only `TreeRead` and is
  framework-free (`STORAGE_RULES_ARE_FRAMEWORK_FREE`).
- One input type: registration and validate mode judge one `TreeRead` that the one reader produced, seen as the
  `PackReadout` that `PackReading` returns; the compose read runs the store half of the same reader (`StoreRead`)
  over the same one walk, inside storage (S7).
- One domain port of the read, `PackReading`; no `TreeReader` port, no `PackInspector`. `packs.application` and
  `pack-validation` name no `packs.infra` type, and every ArchUnit rule stands as it was (S6).
- One read per surface call. Nothing re-reads the staged tree after the judgement; the aggregate is assembled from
  the read that was judged.
- One owner of "held": the `TreeCensus`. A not-held file has an explicit kind; a retired file is never on the
  not-held list.
- Each token is built exactly once, in `PackCodes`, `StorageCodes` or `PlacementCodes` (`PACK_CODES_HAVE_ONE_OWNER`,
  reworded).
- Manifest membership is judged, by a plain function inside `StorageRulesets.verdict`, only when no other
  `VIOLATION` was found, the read faults included and placement's excluded.
- Validate mode judges nothing in a file that is not held (open case 6).
- There is no `Surface` enum, no mode flag on a rule, and no fail-closed guard: a fault the reader meets is a read
  fault of the one read. Registration and validate mode report every read fault. The compose read runs only the store
  half (`StoreRead`, S7), which never produces S2's precheck faults, so S4 needs no filter and there is no second reader.
