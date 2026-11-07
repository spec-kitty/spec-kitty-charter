---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T14:29:16Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP14
---

# WP14 review, cycle 2: changes requested (reviewer-renata)

Reviewed 63cea473 (red) and 29a410d3 (fix) at lane head 0dc5f887, against decision DM-01M6V4WMF8CD8NBN7A065E9ZQ5.

The gate is green. `verifyAll --continue` passed on the head, and `:packs:test --rerun` ran 3196 tests with 0 failures
and 4 skipped.

Every cycle-1 item is fixed, and I re-ran each probe to check:

| Probe | Cycle 1 | Now |
|---|---|---|
| p1a | REPLACED | 409 (REMOVE refused) |
| p1b | REPLACED | 409 (RELATIONSHIP refused) |
| p1c | REPLACED | 409 `artifact_referenced` |
| p4 (rename) | REPLACED | 409 `artifact_referenced` |
| p3 (take another pack's name) | REPLACED | 409 `registration_conflict` |
| p2 (empty leftover from a create) | 500 | the leftover is reclaimed, and the pack is CREATED |

The D11 near-misses are now real pack files, each with a control. The reader race asserts that reads overlapped a
swap, and the create-only race gives one CREATED and seven 412s.

One way to under-report remains, and it blocks approval.

The probes are in
`/tmp/claude-0/-home-user/72e3e65b-b349-5267-927a-2423aaa3f1ac/scratchpad/renata-wp14-c2/service/packs/src/test/java/ai/speckitty/charter/packs/application/RenataProbe2Test.java`
(run in a scratch clone at 29a410d3, not in the lane).

## Blocking

**Issue 1: `salvageStored` drops every edge of a graph document that fails the schema, so a dropped locked edge is
not gated again.** `salvageStored` takes its edges from `store.readGraphs(key).documents()`. `readGraphs` keeps a
document only if it binds to the graph schema; one that does not is a violation and contributes no edges. That is
the same kind of fault that makes `loadStored` refuse in the first place ("a graph that breaks its schema"), so the
salvage path drops exactly the document that triggered it.

`base` locks `LOCKED_ONE`, as in `rigWithBase`. In each probe derived is installed holding the document below, and
the upload `derived 2.0.0` leaves the document out:
- **p6:** `edges.graph.yaml` holds `LOCKED_ONE -> DERIVED_ONE` and a second edge with relation `not_a_relation`:
  **REPLACED**.
- **p6b:** the same locked edge, with an unknown top-level key `extra_key: 1`: **REPLACED**.
- **p11:** a stamped `registered.graph.yaml` holds the locked edge and one bad entry: **REPLACED**.

For comparison, the same edge in a document that binds is refused `409 governance_policy_violation (RELATIONSHIP)`
(p1b).

The decision reads "the edges of any graph document that parses". Each of these documents parses as YAML; only its
schema check fails. The trigger is also realistic. A relation token or a field retired by a later schema has the
same effect as the legacy `references:` field of cycle 1, so packs written before such a change land on this path.

Fix: for the salvage, read each root shard, `drg/fragment.yaml` and the registered shard as plain YAML, outside the
schema.
- Take every entry of `edges:` whose source can be read as a reference, and gate it as a dropped edge when the
  upload lacks it.
- An entry whose relation is unknown still has a source, so key that edge loosely or gate its source with
  `RELATIONSHIP`.
- A document that is not YAML at all contributes nothing, as the decision says.

Add red tests for p6, p6b and p11, each with a control (the same document without the locked edge registers).

## Non-blocking

1. **The file-stem rule under-reports a file whose id differs from its stem. It follows the ruling, but the runner
   should look again.** p7: derived holds `directives/DECOY.directive.yaml` with `id: LOCKED_ONE` plus a legacy
   `references:` field, and the upload drops it. The result is REPLACED: the removal counts as `directive:DECOY`, so
   no `REMOVE` of `LOCKED_ONE` is gated. p7c is the inbound version: base's `A_ONE` sits in a legacy `DECOY` file,
   derived refers to `A_ONE`, and base's upload drops the file. Also REPLACED.

   A file that is not legacy is read under its real id (p7b is refused correctly). The gap is only for a file that
   does not bind but is valid YAML. Reading its `id:` key with a plain YAML parse, and falling back to the stem only
   when that fails, would close it. This needs a change to DM-01M6V4WMF8CD8NBN7A065E9ZQ5, which says "by the id its
   path names", so I do not block on it.
2. **The salvage loses all digests and derived relations when the assembly throws.** `salvageStored` builds the
   whole assembly in one `catch (RuntimeException)`. One file that throws at assembly time, rather than recording a
   read violation, sends every artifact to the stem fallback. All digests are then lost (every kept artifact
   becomes a REPLACE, which fails safe), and so are the derived relations of artifacts that would read. Those
   relations are dropped edges that are not gated. A per-file read would keep them.
3. **Test hygiene.** `salvageStored`'s own test was written after the code, as declared. `SlowSwapCommitStores` is
   out-of-map test support, and the red commit's trailer already names it.

These are probed and not issues:
- p10/p10b: a `LOCKED_ONE` file holding broken YAML or binary content is still listed by its stem and gated.
- p10c: a `.yml` file is unmodelled, so the reads never held it either.
