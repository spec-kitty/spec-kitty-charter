# Pre-spec grounding squad: agent_profile round trip (#190)

Point-cut: pre-spec. Question: *what must the service add so that every `*.agent.yaml` of the
pinned packs round-trips by meaning, and where does it live?* Grounded on `main` 90de8f9 and
`spec-kitty-open-packs` at `openPacksRef` `01edb4e`.

The Spec Kitty CLI could not be installed in this session (permission denied), so each delegate
loaded its profile from `packs/built-in/agent_profiles/<id>.agent.yaml` (the procedure's degraded
read-only fallback; no overlays or lineage applied). Cast: `researcher-robbie` (pack content and
schema), `architect-alphonso` (slice placement and seams), `planner-priti` (gate mechanics and
scope).

## Evidence

- **Schema.** `contracts/packs/schemas/agent-profile.schema.json` is closed at every level, mirrors
  core's runtime `AgentProfile` (`src/charter/offering/agent_profiles/profile.py`) field for field,
  and declares no `specializes_from`, `enhances` or `overrides` key. The only open map is the object
  form of `excluding` (ADR-0036 per-kind table).
- **Census.** 31 files in the pinned open-packs (26 `built-in`, 5 `qa`), 5 vendored (`packs/qa`,
  byte-identical to the pin). Zero schema violations, unknown keys, duplicate keys, multi-document
  files or YAML 1.1 ambiguities. `excluding`, `avatar-image`, `model` and `effort` are never used.
  Directive `code` values (`"003"`) and `schema-version: "1.0"` are quoted strings that look like
  numbers; 193 block scalars end in a newline; 12 files omit `max-concurrent-tasks`, 7 omit
  `description`.
- **Lineage.** No profile carries a lineage field. The five `specializes_from` edges are in
  `built-in/agent_profile.graph.yaml`, all pointing to `implementer-ivan`. The gate round-trips that
  shard already.
- **Edges.** The relation a profile reference carries in the shard is not derivable from the profile:
  of 101 directive references, 84 are `requires`, 14 `suggests` and 3 have no edge.
- **Seams.** The store assumes a kind's schema name equals its token (`PackFileSet.loaded`,
  `YamlSerializationStrategy.read`) and that its id key is `id` (`FilesystemPackStore.locate`, the
  gate's `DomainRoundTrip`). For agent profiles the schema is `agent-profile` and the id key
  `profile-id`. The gate adapter hard-codes the five bound kinds.
- **Slice.** ADR-0035 decision 4 (active) says every kind implements `CharterArtifact` "each from its
  own slice and domain model", agent profiles included, and rejects putting every kind in `packs`.
  ADR-0037 decision 6 (Proposed) defers the cross-slice binding seam "to the first such kind".

## Findings and dispositions

| # | Lens | Sev | Finding | Disposition |
|---|---|---|---|---|
| A1 | architect | HIGH | Putting `AgentProfile` in `packs.domain` contradicts ADR-0035 decision 4. | **accepted**: the entity lives in a new `profiles` module (`profiles.domain`); its DSO, YAML format and binding stay in `packs.infra.storage`, which depends inward on `profiles.domain`. Recorded as an amendment of ADR-0037 decision 6 (plan.md, ADR-0037). |
| A2 | architect, planner | HIGH | Schema name `agent-profile` is not the token `agent_profile`; the kind would never load and would validate against a missing schema. | **accepted**: `KindBinding.schemaName()` (default: the token); `PackFileSet` and `YamlSerializationStrategy` resolve through it. No schema renamed (core consumes `contracts/packs`). #191 has the same need and reuses it. |
| P1 | planner | HIGH | The id key is `profile-id`; `locate` and the gate pair files by `id`. | **accepted**: `KindBinding.idKey()` (default `id`), used by `locate` and the gate adapter. |
| P2 | planner | HIGH | `DomainRoundTrip` hard-codes kinds, file token and id key. | **accepted**: file token and id key come from the binding of the gate kind; the kind list and the save line are appended. |
| A3 | architect | MEDIUM | `KindBindingsTest` requires entity simple name == token. | **accepted**: compare without underscores. |
| A4 | architect | MEDIUM | `profiles.domain.badfixture` is the ArchUnit "unregistered slice" control; registering `profiles` disarms it. | **accepted**: the control moves to an owner that is no slice (`foreign.domain.badfixture`). |
| A5 | architect | MEDIUM | Rules pinned to `packs` leave a new slice partly ungated. | **accepted in part**: `profiles` joins `SLICES` and `DOMAIN_RINGS` and gets a purity rule; the application and storage rules wait for #140 (no profiles application or port exists). |
| R1 | researcher | MEDIUM | An empty `excluding` has no written meaning; no corpus file uses it. | **accepted**: read and written verbatim in both forms; `excluding` joins the gate comparator's open maps; unit tests cover the list, the map, an empty list value inside the map and absence. |
| R2 | researcher | MEDIUM | Number-looking strings (`"003"`, `"1.0"`) must stay strings. | **accepted**: covered by the emitter's dump schema; a format test pins both. |
| P3 | planner | MEDIUM | Binding the kind brings `packs/qa` and open-packs profiles into `check` (`WholePackOpenPacksRoundTripTest`). | **accepted**: acceptance criterion. |
| P4 | planner | MEDIUM | Packs with agent files get a new source fingerprint once (ADR-0037 negative). | **accepted**: stated in the changelog. |
| A6 | architect | MEDIUM | #190 and #191 delete adjacent `expected-red.yaml` entries. | **accepted**: trivial rebase conflict; noted for the merge order. |
| R3 | researcher | LOW | 193 block scalars end in `\n`. | **accepted**: strings are carried verbatim; a format test pins it. |
| R4 | researcher, planner | LOW | The gate does not equate an absent field with its schema default. | **accepted**: every defaulted scalar keeps whether it was declared; the writer writes only what was read. |
| A7 | architect | LOW | `ArtifactEdges` is package-private to `packs.domain`. | **changed**: `relationships()` mints none (the shard is the source of the edges, see Edges). |
| A8 | architect | LOW | A repository port would be a shared edit. | **accepted**: no port; the round trip goes through the generic `Pack` load. Selection (ADR-0032) adds one later. |
| A9 | architect | LOW | `packs.domain` cannot name `AgentProfile`. | **accepted**: no typed view on `Pack`; callers use `artifacts(AGENT_PROFILE, AgentProfile.class)`. |
| P5 | planner | LOW | Planted fixtures change from inspected-only to loaded. | **changed**: the post-plan review (V13) found they are validate-mode fixtures only, not on the load path; nothing to change. |
| P6 | planner | LOW | #187's quickstart names the wrong ratchet message. | **accepted**: corrected to `BASELINED_MODELLED_KIND`. |
| R5 | researcher | LOW | JSON Schema `integer` accepts `50.0`. | **deferred_with_rationale**: no corpus file; validator behaviour is not changed here. |
| R6 | researcher | LOW | `profile-id` has no pattern and need not match the file stem. | **deferred_with_rationale**: a schema change, out of scope; all 31 files agree today. |
| R7 | researcher | INFO | Core's schema-generation model disagrees with its runtime model (`tags`, `role`, no `excluding`). | **deferred_with_rationale**: core hygiene, raised on #190 for the operator to route; the service follows the runtime model. |
| P7 | planner | INFO | Validate mode's duplicate-id check looks only at kinds whose schema declares `id`. | **deferred_with_rationale**: validate mode is out of scope; raised on #190. |

No pack inconsistency was found, so no `spec-kitty-open-packs` issue is filed.

## Post-plan review (reviewer-renata)

Point-cut: post-plan, on the plan and the work in progress. Verdict: changes requested before done;
the design (slice, seam, no minted edges) is sound.

| # | Sev | Finding | Disposition |
|---|---|---|---|
| V1 | HIGH | `GeneratedPackTree` plants `*.agent.yaml` as a decoy, and `PackFileSetExactnessTest` derives its kinds from `KindBindings`; both break once the kind is bound. | **accepted**: agent profiles are a loaded `Kind` there (token `agent`, valid content, ids under `profile-id`); procedure, asset and step-contract stay decoys. |
| V2 | HIGH | `WholePackStoreTest` plants an agent file as the unmodelled example. | **accepted**: it plants `sub/r.yaml`, which no rule maps, so no later kind mission breaks it. |
| V3 | HIGH | A reader that drops a field no corpus file uses (`avatar-image`, `model`, `effort`, `excluding`) would pass every gate. | **accepted**: a fully populated profile round-trips by meaning (`AgentProfileYamlTest.everyFieldIsCarried`), and a test checks that it names every top-level property the schema declares. Nested `$defs` properties are populated in it by hand. |
| V4 | MEDIUM | `max-concurrent-tasks` has no maximum; a large value would be truncated or crash the load. | **accepted**: the DSO holds `long`, a value beyond it reads as `Long.MAX_VALUE`, and the binding refuses anything beyond `int` as a domain violation of that file. R5 (`50.0`) reads as 50, equal by value. |
| V5 | MEDIUM | The `qa` pack could pass vacuously. | **accepted**: `WholePackOpenPacksRoundTripTest` checks per pack that every `*.agent.yaml` on disk is a profile in the aggregate (36 in all). |
| V6 | MEDIUM | SC-001 could be faked through the baseline. | **accepted**: the `expected-red.yaml` diff is the deletion of the `agent-profile` entry only. |
| V7 | MEDIUM | A relationship from a profile now resolves, and a duplicate `profile-id` now breaks every registration into that pack. | **accepted**: pinned by `RelationshipRegistrationServiceTest`, stated in the changelog. Validate-mode duplicate detection stays deferred (P7). |
| V8 | MEDIUM | `KindBindingsTest` validated by token; `YamlFormat` Javadoc said the token names the schema. | **accepted**: both use `schemaName()`. |
| V9 | LOW | `DomainRoundTrip` couples id and token in three places. | **accepted**: all three go through the binding. |
| V10 | LOW | `STORAGE_RETURNS_DOMAIN` allows only `packs.domain` return types. | **deferred_with_rationale**: no public storage method returns a `profiles.domain` type; widening the rule waits for a profiles port (#140). |
| V11 | LOW | Docs listing the kinds or the modules are stale. | **accepted**: `service-lifecycle.md`, `architectural-patterns-applied.md`, `hexagonal-service.md`, `service-build.md`, and ADR-0037 decisions 4 and 6. |
| V12 | INFO | No test pins a real pack's fingerprint. | Noted. |
| V13 | INFO | Planted `.agent.yaml` fixtures are validate-mode only. | See P5. |
| V14 | INFO | `WholePackStore` and `PackLayout` need no change. | Noted. |

The fix also found one gate defect: the comparator treated `specialization-context: {frameworks: []}`
as different from an absent field, though ADR-0036 Decision 2 makes a declared object whose declared
fields are all empty the same as an absent one. **accepted**: fixed in `MeaningComparator`, with a
test and its open-map twin.

