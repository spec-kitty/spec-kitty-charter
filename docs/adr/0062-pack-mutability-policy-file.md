---
title: "ADR-0062: A pack declares its mutability levels in policy.yaml"
description: "The pack mutability policy file: the mutability_policy key, the closed policy schema, the highest level along the closure, and fail-closed reads."
doc_status: active
updated: 2026-11-05T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-05'
type: ADR
kind: ADR
related:
  - adr/0012-pack-mutability-policy
  - adr/0030-removal-is-distinct-from-deactivation
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0020-offering-and-activations
  - adr/0047-generated-indexes-and-merge-queue
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before reading or enforcing a pack's mutability levels, or changing the policy schema.
  - profile: governance-lead
    guidance_text: Read this for how a pack locks an artifact, and why a later pack can raise a level but never lower it.
  - profile: pack-author
    guidance_text: Read this for the shape of policy.yaml and what happens when pack.yaml names one that is missing or invalid.
---

# ADR-0062: A pack declares its mutability levels in policy.yaml

## Status

**Accepted**, 2026-11-05. Decider: Stijn Dejongh (operator). The operator chose the format on 2026-11-05, in the
mission that builds the activation register and its closure (#360): build the reader now, with an optional
descriptor key that names one pack-root file holding a default level per kind and a level per artifact.

## Context

[ADR-0012](/adr/0012-pack-mutability-policy/) gave every artifact one of three mutability levels, `open`,
`extendable` and `locked`. Its decision 2 put the levels in a pack-root policy file that `pack.yaml` references,
holding a default level per kind and entries per artifact URN. It did not fix the file's name, its key in `pack.yaml`
or its shape. Its decision 3 says a later pack may raise a level but never lower it. Its decision 5 moves the built-in
pack's permissions from `replaceable-builtins.yaml` into the built-in pack's own policy file, with a migration.

[ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/) decision 5 added role locks, role pins and other role
integrity settings to the same file, and its decision 7 left their shape to a later record.

The service already had the model: `MutabilityPolicySource` resolves a target to its URN entry, else its kind's
default, else `open`, and the registration write gate (`PackMutabilityPolicy`) refuses a write the level does not
permit with `governance_policy_violation`. But the source was one global, empty value: nothing read a file. The
activation writes of [ADR-0020](/adr/0020-offering-and-activations/) D9 need the levels too, to refuse a deactivation
of a locked artifact with `artifact_locked`.

[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) gave pack-level files (presets, the
manifest) a sanitized, closed schema each, classified in `contracts/packs/file-kinds.yaml` and digested at the end of
the source fingerprint, so that a pack without them keeps its digest.

## Decision

### D1. The descriptor names the file with `mutability_policy`

`pack.yaml` gains an optional key, `mutability_policy`, a pack-relative path to the policy file. In this version the
descriptor schema admits one value, `policy.yaml` (`const`), a file at the pack root. The key is kept, not implied, so
that a reader finds every concern file of a pack from `pack.yaml` (ADR-0012 decision 2), and so that a later version
can widen the value without a new key. A pack whose descriptor does not name the file has no policy: every artifact of
it is `open`, whatever lies at its root.

### D2. The file has a closed schema

`contracts/packs/schemas/policy.schema.json` validates the file, classified by the `files` rule `*/policy.yaml`. It is
closed (`additionalProperties: false`) and holds:

```yaml
schema_version: 1          # required, the integer 1
kind_defaults:             # optional: artifact kind token -> level
  directive: extendable
artifacts:                 # optional: bare URN -> level
  directive:DIRECTIVE_001: locked
```

A level is `open`, `extendable` or `locked`. A `kind_defaults` key is one of the artifact kind tokens of
`file-kinds.yaml`. An `artifacts` key is a bare URN of an artifact kind (`<kind>:<id>`, never pack-qualified). In one
pack, a target's level is its `artifacts` entry, else its kind's default, else `open` (ADR-0012 decision 2).

### D3. The highest level along the closure wins

A target's level is judged along a composition closure, not in one pack: each pack of the closure contributes its own
file, and the answer is the highest level any of them declares (`open` < `extendable` < `locked`), whatever the
closure's order. A later pack therefore raises a level and never lowers it (ADR-0012 decision 3). The answer names the
first pack, in closure order, that declared that level, so a refusal can name it.

One function answers this (`PerPackPolicy.levelIn`), and two callers share it: the registration write gate, which
judges a write into pack P along P's closure, and the activation lock query, which judges a deactivation along the
activation's closure.

### D4. A named file that is missing or invalid makes the pack unreadable

When `pack.yaml` names a policy file that is missing, is not UTF-8 text, is not YAML or breaks its schema, the pack is
unreadable: `pack_unreadable`, naming the pack and the file. It is read where the compose reads each pack, so the
offering and every closure that holds the pack refuse it. A lock that cannot be read is never taken as `open`. No new
refusal code is added.

### D5. Role locks and pins are reserved

The shape of role locks and role pins (ADR-0030 decisions 5 and 7) is not decided here. The schema is closed, so a
`roles` or `pins` key, like any key it does not declare, makes the file invalid and the pack unreadable until a later
record adds them.

## Consequences

### Positive

- A pack author can lock an artifact, and the registration gate enforces it along the closure.
- One function decides the level for writes and activations, so they cannot disagree.
- The file is digested in the source fingerprint after the manifest, so a lock edit moves the fingerprint and a
  consistent read never serves a stale level. A pack without the file keeps its digest list (ADR-0042).

### Negative

- The write gate composes the written pack's closure once per registration. Registrations are rare, so the cost is
  accepted; a closure that cannot be composed refuses the write.
- The whole-pack write surface (`WholePackStore`) lists `policy.yaml` among the unmodelled files and does not copy it,
  as it does any file it does not model. Carrying it through a whole-pack write is follow-up work.

### Notes for pack authors and open-packs

The key exists from this version. No pack is required to ship a policy file, and the open packs ship none yet, so no
registration or activation answer changes until a pack does. A pack that ships one should also declare
`requires_core` (ADR-0012 decision 4), so that an older CLI refuses the pack instead of loading it without its locks;
the service does not enforce that pairing.

## Open questions

1. **Only `policy.yaml`.** The descriptor admits only `policy.yaml` in this version, because the whole-pack gate
   classifies files by path and `*/policy.yaml` is the one rule. Widening the value means classifying the file the
   descriptor names, whatever its path. The operator may rule on whether to widen it.
2. **The built-in pack's own file.** ADR-0012 decision 5 gives the built-in pack a policy file with `extendable` kind
   defaults and the `replaceable-builtins.yaml` migration. Both remain follow-ups.
3. **Open packs.** No open pack ships a policy file yet; when one does, its levels apply with no service change.

## References

- [ADR-0012](/adr/0012-pack-mutability-policy/), decisions 2, 3, 4 and 5: the levels, the file, tighten never loosen,
  `requires_core`, and the built-in migration.
- [ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/), decisions 5 and 7: role locks and pins.
- [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/): pack-level files.
- [ADR-0020](/adr/0020-offering-and-activations/) D9: `artifact_locked` on activation writes.
- `contracts/packs/schemas/policy.schema.json`, `contracts/packs/schemas/pack-descriptor.schema.json`.
