---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-08T07:44:25Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP20
---

# WP20 review, cycle 1: changes requested (reviewer-renata)

Reviewed lane-r c4b6bdaf..939d00a8, plus a look at the merge 0682e960.

## What passed

**The gate.** `verifyAll --continue` is green. `python3 scripts/docs/check_docs.py all wordlist` exits 0.

**The three code items.**

| Item | What I checked | Verdict |
|---|---|---|
| 1. WARN for a dropped non-activatable notice | Red first: in a scratch copy, the reverted mapper fails `aNoticeOnATemplateIsNotInTheResponseAndIsLoggedWithItsUrn`. The contract description and #417 are in place. | Correct |
| 2. Relationship `DELETE` gates the removed edge | Red first: the reverted `RelationshipRemovalService` (the 4718af37 version, on the final fixture) fails `anEdgeWhoseSourceIsALockedArtifactOfAnAncestor...`, so the implementer's claim holds. The gate runs after the 404 and 409 checks and before the impact check. | Correct |
| 3. Qualified references match only same-pack edge ends | Red first: the same revert fails `aDeleteThatNamesOnePackQualifiedTarget...`. The unqualified control passes either way, as a control should. | Correct |

On item 2, `### Changed` is the right class. The relationship `DELETE` was reserved (`501`) on main and is first built in this same PR A, so no released `204` becomes a `409`.

On item 3, the matching rule is sensible and documented. A reference that names no pack keeps the kernel's comparison. A qualified reference matches only an edge end qualified with the same pack, and never matches an unqualified end (404).

**The merge resolution.** The controller takes both `PackSourceIntake` and `PackRemovalController`. `reserved-registration.feature` was deleted, and `ReservedOperationCoverageTest` asserts the pack-registration set is empty. No operation still carries `contractSet: pack-registration`. Fine.

**The restored ADR files.** ADR-0005, ADR-0019 and ADR-0065 are byte-identical to the planning branch (sha256 compared), and unchanged since.

Every other hand-off item I checked is documented, not just mentioned:
- the host requirement, `#234`, the keys and their defaults, the forbidden roots, the resolver union;
- the fixed reasons and the `502`/`504` answers, the `\S+` pattern, `name_mismatch`, the residual local-source race;
- the cut `source.url` and `source.commit`, https only with no redirect, links and submodules, `entry_type`, the sha1 work repository;
- the `428` with `If-None-Match: *`, `bad_ref` and `commit_mismatch`, the `git-source` job;
- the per-artifact `422`s, the author-graph `409`, the preset re-serialization and empty keys, the manifest gap, the facts overlay;
- the queue (two running, eight waiting), the slot taken before the body is read, the 90 s deadline that counts the queue;
- the `409` re-send after a per-artifact `PUT`, the history healed at the time of the repair, the census, the dependent that blocks its ancestor, notices of activatable kinds only.

## Blocking

**Issue 1: the docs describe the unreadable-pack repair as it was before WP14's review fixed it, so they say removals from an unreadable pack are not gated.**

The text says the gate judges a whole-pack upload over an unreadable stored pack as "every uploaded artifact is a replace, and nothing is a removal". It appears in three places:
- `docs/guides/compose-packs.md:269-271`
- `docs/conformance/divergence-register.md:664` (DIV-604)
- `docs/conformance/divergences.yaml:1089` (DIV-604)

That copies WP14's original hand-off line. It is the fail-open behaviour that WP14's review cycles 1 to 4 removed (decisions DM-01M6V4WMF8CD8NBN7A065E9ZQ5 and 01M6V6N74ER28H8S6TSVZ8ZEY2). Today `WholePackStore.salvageStored` reads each stored file on its own:
- An artifact counts by its declared `id`, and also by its file stem when the two differ.
- Graph files are read as plain YAML, entry by entry.
- An artifact the upload leaves out is gated as `REMOVE` and goes through the inbound scan (`409 artifact_referenced`).
- A dropped edge is gated as `RELATIONSHIP` on its source, including an entry whose relation is unknown.
- A stored artifact that does not bind has no digest, so keeping it counts as a `REPLACE`.
- When the stored artifact set cannot be determined at all, the registration is refused `pack_unreadable`.

An operator who reads DIV-604 today concludes that a replace can drop a locked or referenced artifact from a broken pack unchecked. It cannot.

Fix: rewrite the three passages to say all of the above. For example: "The gate and the inbound scan judge the upload against what can still be read of the stored pack, file by file: an artifact or edge the upload leaves out is a removal, gated and scanned as usual; a stored artifact that does not bind counts as changed; a pack whose artifacts cannot be told at all is refused `pack_unreadable`." Cite the two decisions in DIV-604's ruling line.

## Your flagged points

**a. The "core:" lines of DIV-602 and DIV-605 to DIV-613: unsupported, but not blocking.** None of these entries cites core evidence; every evidence line is `charter:`. Nothing in the repository (`docs/conformance`, the ADRs) shows what core does for registration. The register does not require core evidence: most DIV-1xx and DIV-2xx entries have none either. Still, two lines are positive claims about core and carry the most risk:
- DIV-601: "merges per artifact"
- DIV-604: "warns"

Before the PR A closeout, either add a `core:` evidence line (a core path, or "no such command or endpoint in core vX.Y, searched …"), or reword the line as the service's understanding ("Core is not known to …").

**b. Downgrade: the docs are right.** The spec (FR-026: "a version is never reused with other content"), IC-16 ("a registration whose version appears with another revision ends refused … whether or not that version is the stored one") and ADR-0065 line 81 ("Any `pack_version` replaces the stored pack, an older one included. The version is a label, not an order") all allow going back to an older version, and refuse only a reused version with other content. The hand-off's "a downgrade is refused" was shorthand for "a reused version after a downgrade is refused". This is not a WP15 defect.

**c. The `edit` link of `getPack`: fix the contract, not the service.** WP16 decided there is no `edit` relation, and the docs say honestly that it is declared but not sent (`hypermedia-hateoas.md`). A contract that declares a link the service never sends misleads generated clients. Remove `edit` from the `getPack` description (`contracts/charter/paths/packs_packId.yaml:132`) and from `examples/PackRegistration.stored.yaml`, under a `### Changed` fragment. This is a PR A closeout item, not a WP20 blocker.

**d. `registration_timed_out`: both texts are right.** NFR-008 sets the source deadline (60 s) plus 30 s, which is the code's 90 s `DEADLINE`. The status table's "90 second deadline, which counts the time it waited in the queue" and the `x-refusal` "source deadline plus 30 seconds" say the same thing. You could add "(60 s + 30 s)" to the table.

**e. The restored ADRs: identical,** as above.

## For the PR A closeout

- The contract's `edit` link (c).
- Core evidence for DIV-601 to DIV-613 (a).
- Confirm the first green run of the `git-source` CI job, and that the aggregate gate and branch protection require it.
- WP18's follow-ups: native-image `ProcessBuilder` hints, and the PIT floor for `infra.fetch.git`.
