---
title: "ADR-0036: A kind-keyed pack registry and the whole-pack seam"
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
  - context/conceptual-model
  - architecture/logical-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding an artifact kind to the packs slice, or before loading or writing a whole pack.
  - profile: core-maintainer
    guidance_text: Read this for how the service holds a whole pack; nothing on the wire changes.
---

# ADR-0036: A kind-keyed pack registry and the whole-pack seam

## Status

**Proposed** — 2026-10-27, for
[#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189). Needs the operator's
acceptance: it changes the order that
[ADR-0024](/adr/0024-doctrine-artifact-family-contract/)'s amendment of 2026-10-26 set.

Supersedes in part ADR-0024's amendment of 2026-10-26, step 3: a **narrow slice** of the shared
read and write code (#169) is extracted before the new kinds are hand-built, not after. The rest
of #169 (the read and registration services, the controllers) still waits for the rule of three.

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
   YAML strategy keeps one `YamlFormat` per kind. Adding a kind is a binding, a format and a
   schema; nothing else in the slice changes.
4. **The registry does not decide a kind's file layout.** The generic path covers kinds stored as
   one schema-validated YAML file per artifact, which is all five bound kinds. A kind stored
   otherwise (an asset and its blob, an anti-pattern inside a graph) widens the binding when it is
   built, which is where the amendment's rule of three still applies.
5. **The whole pack is loaded from and written to a directory.** The load reads the descriptor,
   every bound kind and the DRG documents (the root `*.graph.yaml` shards and
   `drg/fragment.yaml`, every schema field kept) into the aggregate. Where each artifact's file sits
   is a storage fact, not domain meaning, so the load also returns a **layout** (artifact to
   pack-relative path); the write puts each artifact back at its path, and a new artifact at
   `<plural>/<id>.<kind>.yaml`. The write:
   - refuses a pack that has read violations, since writing it would silently drop content;
   - writes into a staging directory beside the target and renames it into place, as the commit
     store does, so a failure leaves no partial target;
   - refuses a target that already exists. Replacing a live pack stays the commit store's job
     (ADR-0026 decision 3).
   - writes what the domain models and nothing else. Files no kind or pack-level model covers are
     not passed through; the round-trip gate (#187) reports them as not covered.
6. **Kinds owned by another slice are out of this registry.** Agent profiles and mission types may
   live in their own slices (ADR-0035), and ADR-0006 forbids those slices from depending on
   `packs`. Their bindings will need a contribution seam wired by `deployable`; it is built with
   the first such kind, not here. Mission types are not an `ArtifactKind` at all.

## Consequences

### Positive

- Each kind mission (#190 to #195) adds its entity, DSO, binding, YAML format and schema, and
  registers them; it does not edit the aggregate, the store, the strategy or the registration
  services.
- A registration can no longer drop a kind it does not know.
- The round-trip gate has one seam to drive: load a pack directory, write the aggregate to a fresh
  directory, compare.

### Negative

- The typed accessors and the generic map coexist: the five old kinds have both.
- The strategy dispatches by kind at run time; a binding without a format is caught by a test, not
  by the compiler.

### Risks

- The binding could harden around "one YAML file per artifact". Decision 4 keeps the layout out of
  the binding's contract until a kind needs otherwise.
- The layout is outside the aggregate, so a caller that writes a `Pack` without its layout gets
  the canonical paths. The gate and the commit path always carry it.

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
files, which hides the work #190 to #195 exist to do.

## References

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java`
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java`,
  `KindBindings.java`, `WholePackStore.java`
- [ADR-0006: Onion vertical slices with ArchUnit](/adr/0006-onion-vertical-slices-archunit/)
- [ADR-0024: Doctrine artifact family contract](/adr/0024-doctrine-artifact-family-contract/)
- [ADR-0026: The write surface is functional registration](/adr/0026-write-surface-is-functional-registration/)
- [ADR-0035: The shared artifact interface lives in the kernel](/adr/0035-charter-artifact-interface-lives-in-the-kernel/)
