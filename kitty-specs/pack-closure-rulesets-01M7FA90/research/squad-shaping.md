# Shaping squad, 2026-11-16 (after WP01 and WP02)

## Trigger

The trigger is open case 6 of the R2 table. Validate mode judges `charter.yaml` and nested `drg/<kind>.graph.yaml`.
Registration now warns `file_not_held` for those files and does not hold them (Q1, WP02).

The operator's direction (2026-11-16), verbatim:

> "Validate follow registration" in terms of functionality. But I want the registration endpoints to make use of the
> validation, rather than them being separate implementations of the same logic. The idea being that the core layer of
> "packs" contains the consistency and business logic ( and modular validators), the edge services ( api and storage )
> making use of them, the validator module being a validation-edge containing wrapped version of the core validators,
> as well as a CLI entrypoint, and extension of the validations ( serialization concerns, storage checks, etc. ). The
> idea being to keep the core logic agnostic of storage/transfer concerns, and to avoid accidental coupling. Combining
> that approach with oportunities to use composition rather than logical duplication. Dispatch an architecture/shaping
> squad if needed, to ensure the plan and discussed codeshape is viable and in fact has a likelyhood to make the code
> easier to maintain, whilest being ETC ( easy to change ).

## Cast

- `architect-alphonso`: target shape and dependency rules.
- `paula-patterns`: composition, duplication inventory and an easy-to-change (ETC) walk-through.
- `planner-priti`: the plan delta and the sequence.

Each was profile-loaded and read-only. All three verdicts are **VIABLE-WITH-CHANGES**.

## Convergent findings (all three)

1. **Two owners of "held".** The inspector uses the `PackFileKind` table. The store uses `modelledFiles` from the
   assembled aggregate (`WholePackStore.java:288-318,907-933`, `FileSystemPackInspector.java:339-389`). This is the
   root of open case 6. Fix: one held-set derivation.
2. **Several readers of one tree.** Registration reads the staged tree twice: `load`, then `contentsOf` through
   `DomainRepositoryService.compositionFacts`. R3 adds the inspector as a third read, held equal by four parity
   mechanisms. Fix: one reader produces the facts, and the aggregate is assembled from them.
3. **The `Surface` enum is a profile flag inside the domain.** The R1 table is already nested:
   COMPOSE_READ ⊂ REGISTER ⊂ VALIDATE. Fix: named compositions with `ModularRuleset.must`, so the forward subset holds
   by construction.
4. **`PackValidation` is validate-mode wiring sitting in `packs.domain`,** and no main code injects it
   (`CharterWiring.java:87-88`). Fix: move it into `pack-validation` as the validation edge.
5. **Paired emitters** (`findingOf`/`violationOf`, `stepEnhancementFindings`/`…Violations`,
   `substepIdRules`/`…Violations`). Fix: one `Reason`, with the mappers placed at their edges.

## Divergence, and how it was settled

The divergence: are storage-tree checks (manifest membership, reserved shard, not held) core rules over facts the
adapter collects (architect, planner), or storage-layer rules (patterns)? The operator settled it as **storage layer**.
The validation CLI may use them, or the other way round.

## Operator rulings (2026-11-16)

S1 to S6 are recorded here and in the rulings tables of spec.md and plan.md. The decision records of P1, R1 and R3
(`decisions/`) carry a dated note that S1 to S3 supersede them in part; those of P1 and R3 also carry a dated S6 note.

| Id | Question | Ruling |
| --- | --- | --- |
| S1 | One reader on the registration path? | **Yes.** One read produces the facts, and registration, the compose read and validate mode all judge them. The aggregate is assembled from that read. The second read and its fail-closed guard go. |
| S2 | Where do the validate-only checks go? | "api/dto checks are edge, core consistency is domain. prechecks/schema checks are edge." |
| S3 | Who owns manifest membership, the reserved shard and the held set? | "storage layer. the validation cli may choose to use them, or vica-versa." |
| S6 | How do registration and the validation edge reach the storage checks, when the onion rules forbid both to name `packs.infra.storage.rules`? | "Port, no rule change". |

How S2 is read (logged for the operator to correct):

- **Repeated asset id:** a consistency rule. It goes in the domain, so registration gains it.
- **Descriptor version format and asset mime format:** schema prechecks. They go in the edge reader, and with one
  reader (S1) registration gains them too. That is one `### Breaking` fragment.
- **Level and org fragment:** a deployment-config check. It is an edge extension in `pack-validation` only.

How S3 is read:

- The storage checks, the census of files held and not held, and the code tokens they own (`file_not_held`,
  `reserved_shard`, `manifest_membership`) live in `packs.infra.storage`. They are a kernel `ModularRuleset` over a
  storage fact.
- `pack-validation` may name that one rules package. `PACK_VALIDATION_IS_FRAMEWORK_FREE` widens to allow
  `packs.infra.storage.rules`, which itself stays framework-free under its own ArchUnit rule. **Superseded by S6:**
  nothing is widened, and nothing outside `packs.infra.storage` and `deployable` names that package.
- `PACK_CODES_HAVE_ONE_OWNER` becomes "each token is built exactly once" (domain `PackCodes` plus storage
  `StorageCodes`).

How S6 is read (the conflict it settles was raised by the plan's analysis fold: `ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY`
forbids `packs.application` to name `packs.infra.storage.rules`, and `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` and
`PACK_VALIDATION_IS_FRAMEWORK_FREE` forbid `pack-validation` to):

- **The port.** `packs.domain` declares `PackReading` (`PackReadout read(PackRoot root)`), so both edges may name it.
  `PackReadout` carries the pack's `PackContents`, its classified files, its not-held files (`NotHeldFile`: a
  pack-relative path, its kind and reason, so registration's `removeNotHeld` needs no storage type) and one kernel
  `Rationale`: the storage layer's composed check (the read faults, `content()`, `holding()`, and the manifest gate
  when those are clean).
- **Inside storage.** `TreeRead`, `TreeCensus`, `StorageRulesets` and `StorageCodes` stay in `packs.infra.storage`
  (S3 stands), named by nothing outside storage and `deployable`. `FileSystemPackInspector` implements the port by
  composing them (`StorageRulesets.verdict`); its `TreeRead` method is package-private. `deployable` wires it into
  registration and the compose read.
- **The validation edge.** `PackCheck` takes the port from its caller and merges placement's `Rationale` with the
  kernel's `Rationale.and`. `Placement` wraps `PackReadout`. The manifest gate runs inside the readout, so placement
  never gates it, and registration stays a subset of validate mode.
- **ArchUnit.** No rule is widened. `PackReading` replaces `PackInspector`; no `TreeReader` port is built. On R3's
  "no judging port": the core's rules are plain rulesets with no judging port; S6 adds one reading port
  (`PackReading`) whose readout carries the storage edge's verdict.

## Target shape

- **Core (`packs.domain.rules`).** Consistency rules over storage-agnostic facts (`PackContents`, the model view).
  - `PackRulesets.content()` is graph, endpoint, mission type, step enhancement, prompt slot, glossary, asset escape
    and repeated asset id.
  - `closure()` covers cross-pack composition.
  - `PackCodes` holds the domain tokens.
  - There is no `Surface` enum, no `Path`, no bytes, and no YAML or JSON-Schema vocabulary.
- **Storage edge (`packs.infra.storage`).**
  - One reader turns a tree into `PackContents` plus a `TreeCensus`. Parse and schema prechecks are reported as
    neutral read faults, with the schema wording owned here.
  - `PackAssembly` builds the aggregate from the read, without judging.
  - `packs.infra.storage.rules` holds `StorageRulesets.holding()` (not held, reserved shard, manifest membership)
    and `StorageCodes`.
  - The compose read (`DomainRepositoryService`) applies `content()` over its read and keeps its own mapping policy
    (first `pack_unreadable`).
- **API edge (`packs.application`).**
  - `PackRegistrationService`: read once, check `content()` and `holding()`, refuse through `PackRefusals`, assemble
    and stamp. `removeNotHeld` takes the census.
  - `PackRefusals`, the wire refusal mapper, lives here.
- **Validation edge (`pack-validation`).**
  - `PackCheck`, moved from `packs.domain.PackValidation`, runs the same read plus
    `content().must(holding()).must(placement)`. (That shorthand does not type-check against the kernel; the analysis
    fold of 2026-11-16 (U1) states the typed compositions over `TreeRead` and `Placement`, in data-model.md.)
  - Its extensions are the round trip (existing), placement (level and org fragment), and later the pack set (#478).
  - `PackVerdicts` lives here.
  - The CLI entry stays with #478.

## Findings and their dispositions

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| A1 / P1 / Pr1 | HIGH | Two owners of "held" | Accepted: one census in `packs.infra.storage`, used by both surfaces (S3). |
| A2 / P2 | HIGH | Second and third readers, fail-closed guard | Accepted (S1): one reader, `PackAssembly` from the read, guard deleted. |
| A3 / P3 / Pr3 | HIGH | `Surface` enum; validate-only content rules | Accepted: compositions; S2 placement as read above. |
| P4 | HIGH | Storage facts in `PackContents` and `packs.domain` | Accepted (S3): moved to the storage edge. |
| A4 | MED | Serialization knowledge in `PackPolicy` and `PackContents` | Accepted: neutral read faults; schema wording owned by the storage edge reader. |
| A5 | MED | `PackAssembly` placed in `packs.domain` in design-r3 §1 | Accepted: `packs.infra.storage` (WP05 already says so). |
| A6 / Pr2 | MED | `PackValidation` in core | Accepted: becomes `pack-validation`'s `PackCheck`; its tests and the validate golden harness move with it (mutation floor, Pr5). |
| P5 | MED | `PackRefusals` in domain | Accepted: `packs.application`. `PackVerdicts` goes to `pack-validation`. |
| P6 | MED | `PACK_CODES_HAVE_ONE_OWNER` forces storage codes into domain | Accepted: "each token built once". |
| P7 | MED | `CompositionFactsBuilder` duplicates `DomainRepositoryService`'s facts | Accepted: one builder over the one read; `DomainRepositoryService` delegates. |
| Pr4 | MED | ArchUnit forbids the edge naming storage | Accepted (S3): widened for `packs.infra.storage.rules` only. **Superseded by S6:** no rule is widened; the edges reach storage through the domain port `PackReading`. |
| Pr6 | MED | Narrowing the table re-kinds the census gate | Accepted: the WP that narrows it checks the whole-pack baseline and drift. |
| Pr7 | MED | WP09 is ~80 files and atomic | Accepted: split into WP09, WP12 and WP13. |
| A-LOW, P-LOW, Pr8, Pr9 | LOW | Convenience constructors, per-kind rule classes, `pack-check` merge, open case 6 pointer | Folded: lambdas over the existing pure functions; the `pack-check` merge goes into #478's text (no new issue). |
| WP02 review L1 to L3 | LOW | Reason compared as text; retired file listed with the unmodelled reason; wording for mapped kinds | Folded into the census work: an explicit not-held kind, retired files left out, the narrowed table. |

## Plan delta

The planner's sequence is adopted, adjusted for S1 to S3. Owners:

- **WP11 (new):** open case 6 on today's pipeline. Narrow `**/charter.yaml` and nested `*.graph.yaml`; validate mode
  reports `file_not_held` and judges nothing in a file that is not held; one held-set agreement test; golden.
- **WP05 (extended):** the one reader, `PackAssembly`, the census.
- **WP06 and WP07:** compositions instead of `Surface`; storage rules in `packs.infra.storage.rules`.
- **WP09:** validation edge.
- **WP12:** registration and compose read on the one read.
- **WP13:** deletions and ArchUnit.
- **WP10:** records, margins, ADR text.

## Replan open questions (2026-11-16)

The replan (`d3e610387`) raised 12 open questions. The operator ruled on two of them:

- **S4 (Q1):** the compose read keeps today's answers. It ignores S2's new reader prechecks, and that choice is the
  storage edge's own mapping policy.
- **S5 (Q9):** a repeated artifact id answers `pack_unreadable` on the compose read, not a 500. The golden row
  changes once, as a fix.

The orchestrator took the defaults for the rest, consistent with S1 to S3. The operator may revisit them.

| Q | Default taken |
| --- | --- |
| 2 | `DescriptorChecks` stays a pure domain function. The reader calls it as the S2 precheck, and the resolver keeps calling it. That is one implementation, composed in two places. |
| 3 | `TreeCensus` and `TreeRead` live in `packs.infra.storage.rules`. The reader is plugged in by the composition root (deployable, and pack-check's main). The `PackInspector` port does not change shape. **Superseded by S6:** `PackReading` replaces `PackInspector`. |
| 4 | `FileSystemPackInspector` becomes the one reader. The store's DSO read becomes its assembly half. |
| 5 | The wire mapper joins the existing package-private `packs.application.PackRefusals`. |
| 6 | `packs.infra.storage.rules` joins `:packs`' domain+application PIT group. It is pure, so it is held to the same floor. WP10 sets the margin. |
| 7 | The placement tokens (`pack_level_*`, `org_fragment_missing`) are built once, in `pack-validation` (`PlacementCodes`), the only module that emits them. |
| 8 | The `PackValidation` bean goes. Its only main reference is `CharterWiring:87`. The pack doctor (#115) is served by `PackCheck`. |
| 10 | The open-packs draft is moved from the scratchpad into `research/open-packs-drafts/`. WP10 to WP12 append to it, and it is handed to the orchestrator at landing. |
| 11 | `finalize-tasks` refreshes the lanes, the acceptance matrix and the issue matrix. |
| 12 | As the replan wrote it. |
