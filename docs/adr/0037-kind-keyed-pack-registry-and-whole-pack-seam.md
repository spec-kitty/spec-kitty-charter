---
title: "ADR-0037: A kind-keyed pack registry and the whole-pack seam"
description: The Pack aggregate and pack storage key their artifacts by kind, so a new kind only registers itself; a whole pack is loaded from and written to a directory through that registry.
doc_status: draft
updated: 2026-10-27T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0006-onion-vertical-slices-archunit
  - adr/0024-doctrine-artifact-family-contract
  - adr/0026-write-surface-is-functional-registration
  - adr/0035-charter-artifact-interface-lives-in-the-kernel
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - context/conceptual-model
  - architecture/logical-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding an artifact kind to the packs slice, or before loading or writing a whole pack.
  - profile: core-maintainer
    guidance_text: Read this for how the service holds a whole pack; nothing on the wire changes.
---

# ADR-0037: A kind-keyed pack registry and the whole-pack seam

## Status

**Proposed** — 2026-10-27, for
[#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189). Needs the operator's
acceptance: it changes the order that
[ADR-0024](/adr/0024-doctrine-artifact-family-contract/)'s amendment of 2026-10-26 set.

Supersedes in part ADR-0024's amendment of 2026-10-26, step 3: a **narrow slice** of the shared
read and write code (#169) is extracted before the new kinds are hand-built, not after. The rest
of #169 (the read and registration services, the controllers) still waits for the rule of three.

### Amendment 2026-10-27: a kind that spells itself otherwise, and a kind of another slice

Made by [#190](https://github.com/spec-kitty/spec-kitty-charter/issues/190) (agent profiles), the
first kind that needs both. It changes decisions 4 and 6 and no other decision.

- **Decision 4, the mapping.** A kind no longer needs a schema named by its token. Its binding
  names the schema (`KindBinding.schemaName()`) and the field that holds its id
  (`KindBinding.idKey()`), beside the file token it already named. The defaults are the token and
  `id`, so the five kinds bound before are unchanged. The file walk, the schema validation and the
  location of an existing file resolve through the binding. An agent profile is `*.agent.yaml`,
  validated by `agent-profile`, with its id under `profile-id`. No schema file is renamed:
  `contracts/packs` is consumed by core. A mission step contract (#191) uses the same seam.
- **Decision 6 is reversed: a kind of another slice is in this registry.** A kind whose domain
  belongs to another slice is bound in `packs.infra.storage` like any other kind. The store depends
  inward on that slice's domain, and that slice never depends on `packs`. The `AgentProfile`
  entity lives in the `profiles` slice (module `:profiles`, package `profiles.domain`), as
  [ADR-0035](/adr/0035-charter-artifact-interface-lives-in-the-kernel/) decision 4 requires. Its
  DSO, YAML format and binding live in `packs.infra.storage`. `packs.domain` cannot name
  `AgentProfile`, so the `Pack` aggregate has no typed view for it; callers use
  `artifacts(AGENT_PROFILE, AgentProfile.class)`.
  - *Options.*
    - (a) Put the entity in `packs.domain`. Rejected: ADR-0035 decision 4 and its rejected
      alternative.
    - (b) A contribution seam: the profiles slice brings its own binding, wired by `deployable`.
      Rejected for now. The YAML and schema machinery is package-private to `packs.infra.storage`,
      so the slice would duplicate it or force it public, and the seam would reshape the shared
      registry while #191 to #193 append to it. It is built when a kind needs persistence of its
      own.
    - (c) The pack store binds the kind. Chosen: it is one appended binding, and the pack format,
      the schemas and the fingerprint keep one owner.
  - *Consequence for [ADR-0006](/adr/0006-onion-vertical-slices-archunit/).* A slice's
    `infra.storage` may depend on another slice's `domain`, and `packs` becomes the module that
    stores every kind of a pack. No other ring of `packs` may name another slice's domain. The
    architecture test pins that for the inbound adapter (`ENTRYPOINT_STAYS_IN_ITS_SLICE`); the
    domain and application rings were already pinned. If this is to become the pattern for every
    slice, not only for pack-stored kinds, it gets its own record.

## Context

The whole-pack round trip ([#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186))
adds eight kinds in six parallel missions (#190 to #195). Today each kind is wired by hand in the
`Pack` aggregate (a field, an accessor, a builder slot, two invariants), in
`FilesystemPackStore` (a `PackRead` field, a read and a write method), in `SerializationStrategy`
(two methods), in `DomainRepositoryService` (the assembly and the commit-path switch) and in every
registration service, which rebuilds the pack by copying each kind across. Six missions editing
those same places would collide, and a registration service that misses a new kind **drops it
silently** on the next commit.

The amendment deferred the registry because a registry shaped from five identical YAML kinds would
fit the new kinds badly, and because it had no home across slices. Both reasons still hold for the
*read and registration services*. They do not hold for the **plumbing**: the aggregate's per-kind
slots and the storage's per-kind methods are pure repetition, and keying them by kind decides
nothing about how a new kind is shaped.

## Decision

1. **The `Pack` aggregate keys its artifacts by `ArtifactKind`.** It holds a map from kind to the
   artifacts of that kind, with two generic invariants (each artifact belongs to the pack; no two
   artifacts of one kind share a local id) whose messages are the ones the five kinds had. The five
   typed views (`tactics()`, …) stay as one-line delegates; a new kind needs no accessor.
   `Pack.with(artifact)` adds or replaces one artifact and revalidates, so no caller copies kinds
   across.
2. **Storage binds each kind once, in `packs.infra.storage`.** A `KindBinding` maps a kind's
   persistence shape to its entity and back; the `KindBindings` registry lists the bound kinds, and
   the set of loaded and fingerprinted kinds is that list (it replaces `PackFileSet.LoadedKind`).
   `FilesystemPackStore` reads and writes any bound kind through one generic path, and
   `DomainRepositoryService` assembles and commits through the registry.
3. **`SerializationStrategy` is kind-parameterized.** One read and one write take the binding; the
   YAML strategy keeps one `YamlFormat` per kind.
4. **What a kind mission still touches, and where the registry stops.** A kind stored as one
   schema-validated YAML file per artifact, the shape of all five bound kinds, adds its entity, DSO,
   binding, YAML format and schema, and appends one line each to `KindBindings` and `YamlFormats`
   (two shared lists, so parallel missions get trivial merge conflicts there and nowhere else). It
   also needs its rule in `contracts/packs/file-kinds.yaml` to map to a schema whose name is the
   kind's token, which is not yet the case for `agent_profile` (`agent`), `mission_step_contract`
   (`step-contract`) and `glossary_pack` (excluded): that mission settles the mapping. *(Amended
   2026-10-27, see Status: the binding names its schema and id key instead.)* A binding
   names its file-name token (`*.agent.yaml`), so a new artifact is created where the load finds it.
   A kind with its own repository port still adds that port to `DomainRepositoryService`. The
   registry does **not** yet cover, and these missions widen it when they are built (the
   amendment's rule of three):
   - non-YAML content (asset blobs, templates, markdown bodies): the file walk takes `*.yaml` only,
     and the strategy and the whole-pack write move text, not bytes;
   - kinds that are not files of their own (anti-patterns are nodes in a graph shard): a binding
     would write standalone files that must not exist;
   - pack-level files (#195).
5. **The whole pack is loaded from and written to a directory, by `WholePackStore`.** The load
   reads the descriptor and every bound kind into the aggregate. Beside the aggregate it returns:
   - a **layout** (artifact to pack-relative path), since where a file sits is how a pack is
     authored, not what the artifact means; a new artifact goes where a registration would create
     it;
   - the **CRG documents** as authored (the root `*.graph.yaml` shards and `drg/fragment.yaml`, the
     files the fingerprint covers), every schema field kept. They stay out of the aggregate: the
     artifacts mint relationships too, and which copy of an edge is authoritative is an open
     question for the operator, so nothing pretends to keep the two in step;
   - the **unmodelled files**: every other file of the pack, listed so a caller sees exactly what
     the write will not reproduce.

   The write puts the descriptor, each artifact at its layout path and each CRG document into a
   staging directory, then renames it over an empty target it reserved first, so a failure leaves no
   partial target and a target that appears meanwhile is not written over. It refuses, before
   writing anything, a target that exists (replacing a live pack stays the commit store's job,
   ADR-0026 decision 3), a pack with read violations, and a descriptor with undeclared keys, whose
   values are not kept: each would drop content without a word. Unmodelled files are listed, not
   passed through.
6. **Kinds owned by another slice are out of this registry.** Agent profiles and mission types may
   live in their own slices (ADR-0035), and ADR-0006 forbids those slices from depending on
   `packs`. Their bindings will need a contribution seam wired by `deployable`; it is built with
   the first such kind, not here. Mission types are not an `ArtifactKind` at all. *(Reversed
   2026-10-27, see Status: the pack store binds such a kind.)*
7. **Aligned with [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)**
   (round-trip fidelity, Proposed):
   - The CRG reader and writer keep every declared field (node `provenance` and `tags`, fragment
     `body_path`, the shard header fields), and edges compare in canonical form.
   - An unreadable CRG document refuses the whole-pack load as `drg_schema_error` (its decision 3).
     No operation on the wire loads a pack's graph yet, so `drg_schema_error` stays `raised: false`
     in the contract; it changes when a served operation uses this seam.
   - An unreadable artifact file stays a violation on the aggregate, not on the wire: this record
     proposes no contract change for skipped files. The whole-pack write refuses a pack that has
     any.
   - Until `pack-descriptor.schema.json` is closed and `undeclaredKeys()` goes (its follow-up
     work, not done here), the write refuses a descriptor with undeclared keys rather than drop
     them.
   - The CRG documents stay outside the aggregate, so the domain's graph merge is unchanged; whether
     it becomes set-based is decided with the authority of an edge (open on #189).

### Amendment 2026-10-27 (#191): a binding names its schema

The agent-profile amendment above (#190) relaxed Decision 4 and introduced `KindBinding.schemaName()`
and `idKey()`. The mission step contract mission
([#191](https://github.com/spec-kitty/spec-kitty-charter/issues/191)) is the first kind whose schema
is not its token to run the full read and write path through that hook, and wires it through the
store, the strategy, the generic save and the gate adapter: **a binding names its schema, as it
names its file token.**

- `KindBinding.schemaName()` gives the name of the schema that validates the kind's files. It
  defaults to the token, so the five bound kinds are unchanged. `mission_step_contract` names
  `mission-step-contract` and the file token `step-contract`. The agent-profile kind (#190) already
  uses the same mapping (`agent-profile`, `agent`).
- The store's file walk (`PackFileSet`) finds the kind of a schema-mapped file through the registry
  (`KindBindings.bySchema`), never by reading a schema name as a token. A kind whose schema is not
  its token would otherwise load no file, silently. The strategy validates a file against the schema
  its binding names.
- A test ties every binding's file name and schema to `file-kinds.yaml`, the single authority.
- The whole-pack gate's adapter takes its kinds, writes and file tokens from the registry, so a kind
  joins the gate by binding itself.

The relaxation of "a schema whose name is the kind's token" is #190's; this change wires the hook
through the full read and write path. Renaming the schemas instead would change
their `$id`, the gate's kind names and the kebab-case naming every schema uses. The hook is a
one-time change to the store (`PackFileSet`), the strategy (`YamlSerializationStrategy`), the
repository service (a generic save for the gate adapter) and the gate's test adapter. That is the
exception to Consequences/Positive, "does not edit … the store, the strategy"; after it, a kind
whose names differ still only binds itself. Validate mode (`FileSystemPackInspector`) still maps a
schema name to a token by replacing `-` with `_`; aligning it with the registry is left for a later
change.

## Consequences

### Positive

- A YAML kind mission (decision 4) adds its entity, DSO, binding, YAML format and schema, and
  registers them; it does not edit the aggregate, the store, the strategy or the registration
  services.
- A registration can no longer drop a kind it does not know.
- The round-trip gate has one seam to drive: load a pack directory, write the aggregate to a fresh
  directory, compare.

### Negative

- The typed accessors and the generic map coexist: the five old kinds have both.
- The strategy dispatches by kind at run time; a binding without a format is caught by a test, not
  by the compiler.
- Binding a kind adds its files to the source fingerprint and the commit revision, so every pack
  that has files of that kind gets a new `Charter-Source-Fingerprint` and ETag once, although its
  bytes did not change. The order is kept (new kinds are appended), so a pack without such files
  keeps its fingerprint.
- The per-pack load (`PackRepository.load`) and the whole-pack load give the same aggregate but
  differ beside it: the per-pack load names the pack by the identifier asked for and reads no CRG
  documents; the whole-pack load names it by its `pack_id` and returns the layout, the documents and
  the unmodelled files.
- `Pack.with` moves a replaced artifact to the end of its kind, as the registration services did.
- `SerializationStrategy` is public but takes the package-private binding and DSOs, as it took the
  package-private DSOs before: a second format is written inside `packs.infra.storage`.

### Risks

- The binding could harden around "one YAML file per artifact". Decision 4 keeps the layout out of
  the binding's contract until a kind needs otherwise.
- The layout is outside the aggregate, so a caller that writes a `Pack` without its layout gets
  the paths a registration would use. The gate always carries it.
- The whole-pack load takes no lock; a pack swapped by a concurrent commit can be read half before
  and half after. The gate reads packs nobody writes.

## Alternatives considered

### Keep the per-kind wiring until three new kinds exist (ADR-0024's amendment as written)

Rejected for the plumbing only: six parallel missions would edit the same five files, and a missed
registration-service edit loses data. The services keep the amendment's order.

### Put the registry in the kernel

Rejected: the bindings name persistence shapes, which the kernel must not see (ADR-0006,
`KERNEL_IS_PURE`).

### Keep the source path on each artifact

Rejected: where a file sits is how a pack is authored, not what the artifact means; the kind
entities stay free of paths.

### Pass files the domain does not model through the write

Rejected: it would make the round trip look complete while the domain holds nothing of those
files, which hides the work #190 to #195 exist to do. They are listed instead.

### Hold the CRG documents in the aggregate

Rejected for now: the artifacts already mint relationships, so the aggregate would hold two copies
of an edge with nothing to keep them in step, and a registration would leave the documents stale.
Which copy is authoritative is for the operator to settle.

## References

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java`
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java`,
  `KindBindings.java`, `YamlFormats.java`, `WholePackStore.java`, `PackLayout.java`,
  `GraphDocument.java`
- [ADR-0006: Onion vertical slices with ArchUnit](/adr/0006-onion-vertical-slices-archunit/)
- [ADR-0024: Charter artifact family contract](/adr/0024-doctrine-artifact-family-contract/)
- [ADR-0026: The write surface is functional registration](/adr/0026-write-surface-is-functional-registration/)
- [ADR-0035: The shared artifact interface lives in the kernel](/adr/0035-charter-artifact-interface-lives-in-the-kernel/)
