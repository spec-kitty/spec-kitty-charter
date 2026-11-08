---
affected_files: []
cycle_number: 3
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T14:53:38Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP14
---

# WP14 review, cycle 3: changes requested (reviewer-renata)

Reviewed 66b9869b (red) and 93c8ee10 (fix, re-signed from e1f876ef) at lane head b3a0819f, against decisions
01M6V6N74ER28H8S6TSVZ8ZEY2 and DM-01M6V4WMF8CD8NBN7A065E9ZQ5.

The gate is green. `verifyAll --continue` passed on the head, and `:packs:test --rerun` ran 3202 tests with 0 failures
and 4 skipped.

The per-file salvage closes every bypass I found. Each earlier probe is now refused on the right operation:

| Probes | Result now |
|---|---|
| p1a, p1b, p1c | 409 (REMOVE, RELATIONSHIP and `artifact_referenced`) |
| p3 | 409 `registration_conflict` |
| p4 | 409 `artifact_referenced` |
| p6, p6b, p11 | 409 RELATIONSHIP |
| p7, p7b | 409 REMOVE |
| p7c | 409 `artifact_referenced` |
| p10, p10b | 409 REMOVE |
| p2 | the leftover is reclaimed |

I also tried to defeat the salvage. These are all gated, with a 409 on the right operation:
- a quoted source;
- a qualified source (`base::directive:LOCKED_ONE`);
- an entry with no target;
- a flow-style `edges:` list;
- an entry given through an anchor and alias;
- two files that declare the same id (dropping both gives REMOVE; keeping one byte-identical gives REPLACE, which
  fails safe).

One thing blocks approval: the cost.

The probes are in
`/tmp/claude-0/-home-user/72e3e65b-b349-5267-927a-2423aaa3f1ac/scratchpad/renata-wp14-c3/service/packs/src/test/java/ai/speckitty/charter/packs/application/RenataProbe3Test.java`
(run in a scratch clone at 93c8ee10).

## Blocking

**Issue 1: the salvage is quadratic and runs under the registry lock, which takes the pack domain down for minutes.**
For each listed file, `salvageFile` calls `store.readOne(key, binding, id)`. `readOne` calls `locate`, which:
1. lists the whole pack again (`PackFileSet.files`, a walk of the pack);
2. parses every file of the kind as YAML until one declares the id.

So N files cost N pack walks and about N²/2 YAML parses. All of it runs in `decide`, under the registry lock and
the pack lock.

Measured with probe q6: a stored pack of N directives, one of them legacy, replaced by its fixed version.

| N | Readable stored pack | Salvaged |
|---|---|---|
| 750 | n/a | 10.6 s |
| 1500 | 2.3 s | 45 s |
| 3000 | n/a | 157 s |

Extrapolated to the archive bound of 10,000 entries, a salvage takes about 29 minutes.

The registry lock has a 5 s `tryLock`. For that whole time, every other pack write in the JVM (registrations,
per-artifact PUTs, relationships, DELETEs) is refused 503, and the job holds one of its two running slots. The
trigger is ordinary: one legacy file in a stored pack. That is the case this path exists for. To answer the question
in the hand-off: no, the cost is not acceptable within the archive bounds.

`readOne` by id also picks the first file that declares the id, which is not always the file being salvaged. With
duplicates, the digest and the derived relations come from the wrong file. That fails safe, but it is wrong.

Fix: make the salvage linear.
- List the pack once.
- For each listed file, bind that file itself: `strategy.read(kind, text, file)` on the text already read, plus its
  companions, as `readOne` does after `locate`. That needs a package-level helper in `FilesystemPackStore`, for
  example a `readAt(directory, kind, file)` that `readOne` also uses.

Add a test that bounds the cost. For example, count the YAML parses or the file reads, and assert they grow linearly
in N (say at most 3 per file), rather than timing it.

## The implementer's calls

- **An untyped entry kept loosely as source|target:** acceptable. It is conservative: an added edge from a locked
  source is gated as "added" anyway, so the pair match only frees unlocked sources.
- **An unbindable locked artifact cannot be re-uploaded as is:** acceptable. A readable pack cannot replace an
  artifact its ancestor locks either. Identical legacy bytes would also fail `loadStaged`, so a byte-identical
  re-upload cannot happen.
- **The ruling id cited wrongly in the commit message:** cosmetic. Fix it if a commit is made anyway.

## Non-blocking

These under-report, but none of them names something the live reads ever served, since the strict readers reject
each one too. I accept them under the ruling:
- q1: `edges:` written as a map: REPLACED.
- q2i: a graph file holding two YAML documents: REPLACED.
- q2c: a source with no kind: REPLACED.
- q2d: a source with a trailing space: REPLACED.
- q3: `id: [LOCKED_ONE]` in a DECOY file: REPLACED.

If you want them closed anyway: treat the values of an `edges:` map as entries, read every document of a
multi-document file, and refuse `pack_unreadable` when a graph file's `edges:` is present but neither a list nor a
map.
