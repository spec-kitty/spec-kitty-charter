---
affected_files: []
cycle_number: 1
mission_slug: verified-source-fingerprint-01M5PG7Q
reproduction_command:
reviewed_at: '2026-10-24T08:59:46Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review feedback (reviewer-renata, cycle 1)

The checks pass, and most of the work is good: the contract diff changes only descriptions and the
one `x-source`, and `:contracts:check` (rerun with `--rerun-tasks`), `ContractDescriptionsTest`
(3/3, `--rerun`), `check_docs.py all`, markdownlint and cspell are all green. ADR-0018 keeps the
D8/D11 decision text verbatim. The rejection is for wrong claims in the contract prose and in the
docs, plus a CHANGELOG that WP03 cannot fix later. Each one is a small text fix.

## Must fix

**Issue 1: `contracts/charter/schemas/CharterRefusalCode.yaml` lines 6-9 are wrong about the code.**
The text says "Tactic reads never open pack.yaml" and "pack_not_found means the identifier names no
configured pack". Both tactic reads go through `DomainRepositoryService.resolve`
(`service/packs/.../infra/storage/DomainRepositoryService.java`). When the identifier is not a
configured name, `resolve` calls `declaresId` for every configured location, and `declaresId` calls
`store.readDescriptor`, which opens and parses each pack's `pack.yaml` to compare `pack_id`. So
tactic reads do open `pack.yaml`, and an identifier can also resolve through a declared `pack_id`.
What is true is narrower: `declaresId` swallows the refusal, so a missing or invalid `pack.yaml`
never refuses a tactic read. It only stops that pack from matching by `pack_id`. `pack.yaml` is
digested, so a race on it is `stale_input`.
Fix: delete "Tactic reads never open pack.yaml." and say what happens instead. For example:
"pack_not_found means the identifier matches no configured pack, neither by its configured name
nor by the pack_id its pack.yaml declares. A missing or invalid pack.yaml never refuses a tactic
read; it only keeps that pack from matching by pack_id." Then use the same qualifier, or a short
form that points to it, in `paths/packs_packId_tactics.yaml`,
`paths/packs_packId_tactics_tacticId.yaml` and `responses/TacticNotFound.yaml`. These files say
"An identifier that names no configured pack". Keep the pinned phrases verbatim.

**Issue 2: `docs/api/service-contract.md` lines ~119-121 still say designed-only things are built.**
This WP edited the sentence, which now reads "The action-context walk (#95) and the reserved 1.0
surface (`depth`, `ServiceInfo`, the source fingerprint inputs and the per-worktree token) are
decided and built". PR #139 removed the action-context operation and its `Depth` parameter (see
the contract CHANGELOG entry "Tactic retrieval; pack listing and action context removed"). Neither
is built now. This breaks T003's validation ("no designed-only operation is described as built")
and FR-011.
Fix: say that the walk and `depth` are decided (ADR-0018) and that the operation was removed in
PR #139 and is not served. Only `ServiceInfo`, the fingerprint inputs and the token are built.

**Issue 3: `docs/api/service-contract.md` section "The action-context decision (#95)" (line ~55).**
The section sits under `## Built surface` and uses the present tense ("decides what the read
answers"), so it reads as built. Out-of-scope wording is acceptable, but frame it as design
history. Add one lead sentence such as "The operation is not served in this version (removed in
PR #139); the decision below stands for when it returns.", or move the section out of
`## Built surface`.

**Issue 4: `docs/api/service-contract.md` line ~151 names removed schemas as existing.**
This WP edited this line too. It still says "a required `_links` on every existing success schema
(`PackListing`, `PackSummary`, `ActionContext`, `ServiceInfo`)". `PackListing`, `PackSummary` and
`ActionContext` were removed with PR #139; `contracts/charter/schemas/` now holds `ServiceInfo` and
`Tactic`. Fix: name the success schemas that exist (`ServiceInfo`, `Tactic`), or say that the
removed ones get `_links` when they return.

**Issue 5: the scenario titles in `contracts/charter/CHANGELOG.md` do not match WP03.**
The entry lists "A tactic read of a pack whose directory is missing is refused with
pack_unreadable" and "A missing sibling pack directory refuses a tactic read of a healthy pack".
`tasks/WP03-verified-tactic-reads.md` (T013 step 2, line ~187) defines one scenario for this case:
"A configured pack whose directory is missing refuses every read". It configures a missing pack
and reads the healthy `qa`, which is the sibling case. WP03 does not own `contracts/charter/CHANGELOG.md`,
so the promise that "WP03 corrects the entry" cannot be kept. The WP01 prompt and the WP03 prompt
disagree. WP03 builds the scenario, so follow WP03.
Fix: replace the two titles with the one WP03 title, and keep the other titles as they are (they
match). Note the coordination in the move-task note.

## Should fix (low)

- `docs/adr/0018-action-context-read-and-seam-access-surface.md:261-265`: the D11 annotation has
  no indent and no blank line before it, so it renders as a lazy continuation of the "Residual
  window" bullet. It also says "The residual window below" when the bullet is above it. Indent it
  two spaces under that bullet, as the 2026-10-20 annotation is indented, and say "above".
- `contracts/charter/headers/CharterSourceFingerprint.yaml:10-11`: a short orphan line is left
  ("Set\n  once. The freshness work (#73)\n  compares it"). Reflow it. This is cosmetic, and the
  folded scalar is unaffected.
- `docs/api/service-contract.md:87` and `docs/architecture/service-lifecycle.md:74`: the pasted
  definition is one very long line, while the text around it wraps at about 100 columns. Wrap it
  to match. The text must stay identical.

## For the record (no change needed in WP01)

- The spec's Key Entities definition (spec.md) is shorter than the definition the WP prompt says
  to copy. It lacks the kind list, "then" and "under the project configuration directory". All
  WP01 surfaces use the prompt's text identically. Align spec.md at closeout.
- `CharterRefusalCode.yaml`'s own `x-source` points to the dead path
  `service/charter-domain/.../RefusalCode.java`. That is outside this WP's mandate (T004 limited
  the fix to ServiceInfo). File a follow-up.
