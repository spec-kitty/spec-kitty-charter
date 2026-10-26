---
title: "ADR-0012: The owning pack declares a mutability level per artifact: open, extendable or locked"
description: A Charter Pack author sets what later packs may do with each artifact it introduces; the levels replace the consumer-side replaceable-builtins allowlist.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - architecture/resolution-model
  - architecture/logical-model
  - architecture/descriptors
audience:
  - profile: pack-author
    guidance_text: Read this for how you declare what other packs may override, extend or deactivate in your pack.
  - profile: governance-lead
    guidance_text: Read this for how an org locks guidance its projects must keep, and what required now means.
  - profile: core-maintainer
    guidance_text: Read this for the requests to core, the migration of replaceable-builtins.yaml and the precedence defect to fix first.
---

# ADR-0012: The owning pack declares a mutability level per artifact: open, extendable or locked

## Status

**Accepted (provisional)** — 2026-10-19. Decider: Stijn Dejongh (operator). Like every
decision taken on 2026-10-19, this one is provisional: it is revisited as the system evolves,
on the triggers under [Revisit when](#revisit-when). Tracked by epic
[#55](https://github.com/spec-kitty/spec-kitty-charter/issues/55) and its steps #57 to #61.

Clarification 2026-10-19 (the decision is unchanged): the org precedence prerequisite is met.
Core's `59d190a7` makes the later-declared org pack win for every URN and every restated edge,
matching the documented rule, and closes #87. The passages below that call precedence
inconsistent describe the state when this record was written.

Superseded in part by [ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/) (2026-10-26): removal by a derived pack is an operation of its own, allowed on `open` and `extendable` and refused on `locked` (decision 1); the policy file also holds role locks and pins (decision 2).

Superseded in part by [ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/) (2026-10-26): requirement is authored as a preset and the required set is derived from it; `required_*` is no longer the authoring form (decision 7).

## Context

A Charter Pack author cannot say what a later pack may do with the artifacts the pack
introduces. Today:

- **Override is open in three places.** Same-id shadowing in the artifact repositories is a
  field-level merge with a collision warning (core ADR
  [`3.x/2026-05-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-16-1-doctrine-layer-merge-semantics.md)).
  Same-URN node substitution in the DRG merge raises only an `org_override` or
  project-override warning (`src/charter/offering/drg/merge.py:937-981`, `:1302-1311`). The
  declared `overrides:` and `enhances:` relations cover five kinds (core ADR
  [`3.x/2026-05-24-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-24-2-pack-augmentation-vocabulary.md)).
- **The only gate is a consumer allowlist.** An unsanctioned org override of a built-in node
  makes `doctor charter-packs` exit 1
  (`src/specify_cli/cli/commands/_charter_pack_collect.py:975-980`). The sanction comes from
  `replaceable-builtins.yaml`, written by the consumer or shipped by the overriding pack (core ADR
  [`4.x/2026-10-05-3`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-05-3-org-packs-ship-their-builtin-override-sanction.md)).
  The owner of the overridden artifact has no say, and nothing covers org over org or project
  over org.
- **`required_*` guarantees presence, not content.** An org `required_<plural>` entry is
  merged into the activated set (`action_governance_bundle.py:357-378`,
  `interview.py:526-545`), but a project overlay can still change the artifact's fields, and a
  user deactivation of it is silently undone.
- **The obvious words are taken.** A directive's `enforcement: required | lenient-adherence |
  advisory` field (`src/charter/offering/schemas/directive.schema.yaml:54-60`), org
  `governance_policies[].enforcement` (`org_charter.py:115-128`) and `required_<plural>`
  (`org_charter.py:183-193`) already mean other things.
- **The descriptors are closed.** `pack.yaml` (`pack_descriptor.py:85`), `org-charter.yaml`
  (`org_charter.py:155`) and every artifact model reject unknown keys, so a new key in any of
  them makes an older CLI refuse the pack.
- **Org precedence is inconsistent.** The docs say "later wins"
  (`docs/architecture/org-doctrine-layer.md:100-104`), `merge.py:1060-1061` keeps the first org
  node for a URN that is not built-in, and `merge.py:972` keeps the last one for a built-in URN.

## Decision

1. **Three levels, each a set of allowed operations.** A level says which of the three
   operations a later pack may apply to the artifact: *override* (replace or change its
   fields), *enhance* (an `enhances` relation) and *deactivate*.

   | Level | Override | Enhance | Deactivate |
   |---|---|---|---|
   | `open` | yes | yes | yes |
   | `extendable` | no | yes | no |
   | `locked` | no | no | no |

   `locked` is the level the drafts called `enforced`; the name avoids the three existing uses
   of "enforcement". Defining levels as operation sets lets a finer flag be added later without
   migrating declared levels.

   *Superseded in part by ADR-0030: a derived pack may also remove an `open` or `extendable` artifact pack-wide; only `locked` is fully protected.*
2. **Declared in a pack-root policy file that `pack.yaml` references.** The policy file holds
   a default level per kind and entries per artifact URN. `pack.yaml` names the file, so a
   reader finds every concern file of a pack from the main pack file. An artifact the file does
   not name, in a pack with no policy file, is `open`.
   *Superseded in part by ADR-0030: the policy file also holds role locks, role pins and other integrity settings of roles.*
3. **Tighten, never loosen.** A later pack may raise the level of an artifact it did not
   introduce (an org pack may lock a built-in `open` directive for its projects). It may never
   lower it.
4. **A pack with a policy file declares a minimum core version** (`requires_core`), so an older
   CLI refuses the pack loudly instead of loading it without its locks.
5. **The levels supersede `replaceable-builtins.yaml`, with a migration.** Permission to
   override now comes from the owner's level, not from a consumer or overriding-pack sanction. The
   migration:
   - The built-in pack ships its own policy file. Its kind defaults are `extendable`, which
     keeps today's rule that an org may not replace a built-in without permission. Every URN
     that a released `replaceable-builtins.yaml` sanctions today is listed as `open`.
   - An upgrade migration reads each consumer-side and pack-root `replaceable-builtins.yaml`.
     A sanction for a URN the owner leaves `open` is dropped as redundant. A sanction for a URN
     the owner does not leave `open` is reported, naming the owning pack, because a consumer
     cannot loosen the owner's level.
   - For one release the old file is still read, and each sanction it carries is reported as
     deprecated. After that release it is ignored.

   The operator confirmed this migration on 2026-10-19: the built-in pack's kind defaults are
   `extendable`, and every URN sanctioned today is listed as `open`.
6. **`governance_policies.enforcement` is untouched.** It governs interview answer fields, not
   artifacts.
7. **`required_*` means presence only.** A required artifact is always active. Its content is
   as open as its level says. Deactivating a required artifact is refused, and the error names
   the owning or requiring pack ([#67](https://github.com/spec-kitty/spec-kitty-charter/issues/67)).
   `locked` implies required.
   *Superseded in part by ADR-0031: the required set is derived from a preset's must-have items, not authored as `required_*` lists.*
8. **The rule is stated over merge order, not over a fixed tier ladder.** The owner of an
   artifact is the pack that introduced it. Every pack merged after it is a later pack, and the
   level binds every later pack: org over built-in, org over org, project over either, and any
   tier added later. This needs one consistent org precedence, so core's precedence defect is
   fixed first (see [Requests to core](#requests-to-core)).
9. **Violations are refusals, and policy reaches the API additively.** A later pack that breaks
   an owner's level is refused with `governance_policy_violation`, the code ADR-0009 reserves
   in contract 1.0. The finding names the artifact, the level, the owning pack and the
   violating pack. Levels and findings reach the API as a new contract set of additional
   operations, which clients adopt when they choose (ADR-0009); no 1.0 response changes.

## Consequences

### Positive

- The pack author, who knows what must not change, decides it, and the decision travels with
  the pack.
- One rule covers every pack order, including org over org, which nothing covers today.
- A required artifact can no longer be deactivated silently.
- `pack.yaml` stays the one entry point a reader needs to find every file of a pack.

### Negative

- **Consumers lose their veto.** Today a project can refuse an org pack's replacement of a
  built-in by withholding the sanction. Under this decision the owner's level alone permits it.
  A project that wants to keep a built-in must now rely on the built-in pack's level.
- Every pack that ships a policy file needs a CLI new enough to read it.
- The migration touches a file core shipped two weeks ago (core ADR `4.x/2026-10-05-3`).

### Risks

- Packs authored before this decision carry no policy file, so all their artifacts are `open`.
  Org authors must add policy files before the levels protect anything.
- Acceptance scenario `PR-CHARTER-SHARING-002` ("a project's own governance choice wins over
  the team pack it adopts", `tests/scenarios/charter/PROMISES.md:34`) now holds only "unless
  the pack author locked it". The scenario needs that qualifier.

## Alternatives considered

### Keep `replaceable-builtins.yaml` alongside the levels

An override would need both the owner's level and the consumer's sanction. This kept the
consumer veto and needed no migration. Not chosen: it keeps two mechanisms for one question,
and the operator chose a single, owner-declared mechanism.

### A field on each artifact, or a key in `pack.yaml`

Local and visible, but an older CLI rejects every artifact or pack descriptor that carries the
new key, and a per-artifact field has no pack-wide default.

### Two levels, or per-operation flags

Two levels (`open`, `locked`) cannot express "extend but do not replace". Free per-operation
flags give the most combinations to test and explain. The operation-set definition keeps the
door open to flags later.

### A fixed three-tier ladder, or N named tiers

A fixed ladder leaves org packs free to override each other's locked artifacts. Named,
configurable tiers are expressive, but nobody needs them yet; merge order already orders every
pack.

## Revisit when

- Projects ask for their veto back over a specific org replacement.
- Pack authors routinely need a combination of operations no level offers.
- Org authors pair almost every `required_*` entry with `extendable` or `locked`; then a
  shortcut, or "required implies extendable", may be worth its cost.
- Core schedules delegated governance (#2594), which planned to absorb the override sanction.

## Requests to core

- **Fix org precedence first.** Make one rule hold for every URN, and make the docs match:
  `merge.py:1060-1061` and `merge.py:972` disagree, and
  `docs/architecture/org-doctrine-layer.md:100-104` states a third rule. To be filed as a core
  defect.
- **Amend core ADR `4.x/2026-10-05-3`.** Proposed text: "Amendment 2026-10-19: superseded for
  the decision of whether an override is permitted by spec-kitty-charter ADR-0012. The owning
  pack's mutability level decides; `replaceable-builtins.yaml` is read for one release as a
  deprecated input and then ignored. The built-in pack ships a policy file whose kind defaults
  are `extendable` and which lists as `open` every URN sanctioned today."
- **Amend core ADRs `3.x/2026-05-16-1` and `3.x/2026-05-24-2`.** Proposed text for each:
  "Amendment 2026-10-19: a field-level merge (2026-05-16-1) or a declared `overrides` /
  `enhances` relation (2026-05-24-2) applies only where the owning pack's level permits the
  operation (spec-kitty-charter ADR-0012); otherwise the merge refuses with
  `governance_policy_violation`."
- **Correct the docs** that promise the project tier always wins
  (`docs/architecture/org-doctrine-layer.md:106-109`, `:380-383`).

## References

- Epic [#55](https://github.com/spec-kitty/spec-kitty-charter/issues/55), steps #57 to #61;
  [#67](https://github.com/spec-kitty/spec-kitty-charter/issues/67).
- [Resolution model](/architecture/resolution-model/), [Descriptors](/architecture/descriptors/).
- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) (contract 1.0 reservations and additive contract sets), ADR-0013 (persistent
  deactivation).
