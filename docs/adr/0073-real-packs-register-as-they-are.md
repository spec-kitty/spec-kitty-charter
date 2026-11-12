---
title: "ADR-0073: Real packs register as they are: the pack document, excluded files, and readers during a swap"
description: The calls slice B made beyond the operator's rulings on #451, #431 and #490, each with the alternatives it rejected, for the operator to accept.
doc_status: draft
updated: 2026-11-12T00:00:00Z
decision_status: Proposed
decision_date: '2026-11-12'
type: ADR
kind: ADR
related:
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0065-pack-registration-replaces-the-whole-pack
  - adr/0040-content-form-and-payload-kinds
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0031-requirement-is-authored-as-a-preset
  - adr/0019-packs-resource-model-and-hypermedia
  - guides/enforce-org-presets
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before changing how a registration treats a file no rule models, how the pack document is served, or how a read behaves during a pack swap.
  - profile: pack-author
    guidance_text: Read this for why a Markdown note in your pack registers but is not held, and why pack.md, PROVENANCE.md and org-charter.yaml are refused by name.
  - profile: contract-consumer
    guidance_text: Read this for the two forms of GET /packs/{packId}/doc, the shared ETag and Vary Accept, and the file_not_held diagnostic.
  - profile: core-maintainer
    guidance_text: Read this for what core's seam stops reading (pack.md, org-charter.yaml, active_charter) and what a reader sees during a replace.
---

# ADR-0073: Real packs register as they are: the pack document, excluded files, and readers during a swap

## Status

**Proposed**, 2026-11-12, by the slice B mission (`slice-b-real-packs-register-01M7649C`,
[#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)). Decider: Stijn Dejongh (operator); acceptance
pending, asked for on the slice B pull request.

The operator ruled on 2026-11-11 on [#451](https://github.com/spec-kitty/spec-kitty-charter/issues/451) (the README is
the pack document, `pack.md` and `PROVENANCE.md` are retired, unmodelled Markdown is excluded, the document is served
at `/doc`), [#431](https://github.com/spec-kitty/spec-kitty-charter/issues/431) (`org-charter.yaml` and
`active_charter` are retired; org enforcement is a governing preset plus `policy.yaml`) and
[#490](https://github.com/spec-kitty/spec-kitty-charter/issues/490) (readers ride out a swap without a read lock).
Those rulings are recorded where they amend a record: ADR-0042 decision 8 (amendment 2026-11-11), ADR-0036, ADR-0031,
ADR-0065 D1, ADR-0019 and ADR-0040, each with a dated note.

This record holds the calls the mission made **beyond the letter of the rulings**: the nine calls of the mission's
spec ("Calls made by the mission") and the decisions D1 to D9 of its research, as built. The plan-point adversarial
squad reviewed each one (verdict PROCEED-WITH-FOLDS, 3 of 3); its dispositions are in
`kitty-specs/slice-b-real-packs-register-01M7649C/research/squad-plan-point.md`. The doc read's contract shape was
decided by a spike, `research/doc-read-spike.md` in the same mission, accepted by the operator on 2026-11-11. The code
of slice B implements every decision below, so they are in force for that code; the record stays Proposed until the
operator accepts it.

## Context

Before slice B, no real pack registered as it is. A registration refused every file that no rule modelled as
`unmodelled`, and every open-packs pack ships one: a `README.md`, a `PROVENANCE.md`, an `org-charter.yaml`, a
mission template folder's `README.md` or `plan-field-declaration.yaml` (the usability runs of 2026-11-09 and
2026-11-10). The pack document was `pack.md`, which few authors keep, and nothing served it. Separately, a read that met
a pack in the middle of a replace could answer `404 pack_not_found` or `500 pack_unreadable`, and a verified read
answered `500 stale_input` for the whole swap window (#490).

The rulings settle the shapes. They leave open how an excluded file is treated at registration, how the fallback for
Markdown is expressed, how the document is negotiated and cached, how a retired descriptor key is refused, and how a
reader tells a swap from a broken pack. Each call below answers one of these.

## Decision

### 1. A registration refuses no excluded file (call 1, research D1)

A file that an `exclusions` rule of `contracts/packs/file-kinds.yaml` names is not refused at registration, whatever
its extension. The ruling names unmodelled Markdown, but #451's own acceptance also needs the already excluded
`missions/*/templates/README.md` and `plan-field-declaration.yaml` to register. So the rule is "no exclusion is a
refusal", not "no Markdown exclusion is a refusal".

*Alternative rejected:* only Markdown exclusions. #451's acceptance would still fail on the YAML exclusions.

### 2. Unmodelled Markdown is a fallback section, `ignored`, not an exclusion glob (call 2, D2)

`file-kinds.yaml` gains a last section, `ignored`, with one rule: `**/*.md`, with a reason. It is consulted after
`files`, `exclusions`, `retired` and `payloads`, and after the companion claims of the kind bindings, so a skill body
or a toolguide guide stays a companion. A file it matches is **excluded**: not held, not served, not refused, not
digested. It matches case-insensitively (`NOTES.MD` is Markdown too). An `ignored` rule is never among the rules the
overlap check compares. "Excluded" is the one term for both an `exclusions` match and an `ignored` match.

*Alternatives rejected:* a `**/*.md` entry under `exclusions`. Exclusions are consulted before `retired` and
`payloads`, so it would turn the retired `pack.md` and `PROVENANCE.md` into exclusions, drop Markdown assets and
templates from the load, overlap every Markdown `files` rule and make companion resolution refuse skill bodies. A
hard-coded `.md` check in Java was rejected too: the table stays the single authority.

### 3. A case variant of a root rule file is refused by name (FR-022, squad R3)

A root file that differs only in letter case from the file of a root-anchored `files` or `retired` rule (`Readme.md`,
`Pack.md`, `PROVENANCE.MD`, `Org-Charter.yaml`) is refused by name under that rule, before the fallback is consulted.
It is never excluded and never `unmodelled`. A file system that folds case would otherwise read `Readme.md` as the
document on one machine and exclude it on another.

*Alternative rejected:* letting the fallback take it. The author would never learn that the document was not read.

### 4. Excluded files are reported and stripped, not silently dropped (call 7, FR-021, squad A1 and R1)

A registration lists every file of the staged tree it does not hold and reports each one in the answer's
`diagnostics` as a `file_not_held` warning, a new `DiagnosticCode`, whose `detail` names the path and the rule's
reason (`docs/notes.md: not held (<reason>)`). The files are removed from the staged tree **before** the revision is
computed and the tree is committed, so the stored pack holds none of them, and a registration that differs from the
stored pack only in such a file ends `unchanged`. ADR-0065 D1's "holds exactly the pack that was sent" becomes "holds
exactly the pack that was sent, less the files it reports as not held" (amendment 2026-11-11 there). The whole-pack
gate judges an excluded file with a status of its own, `EXCLUDED` (absent from the written tree), so it is neither red
nor added to `expected-red.yaml`; validate mode lists an excluded file with the rule that excludes it, and reads none.

*Alternatives rejected:* dropping the files without a word, which contradicts ADR-0065 D1 and hides the reason from
the registrant; committing them as they are, which would hold files that are undigested and go stale.

### 5. The pack document is `README.md`, in the place `pack.md` had (research D3)

`*/README.md` replaces `*/pack.md` as the `files` rule of the `pack-document` structure schema, in the digest tail
(presets, `README.md`, `pack-manifest.yaml`, `policy.yaml`, then the retired files) and in the gate model. `pack.md`,
`PROVENANCE.md` and `org-charter.yaml` are root-anchored `retired` rules, refused by name with a reason, in
classification, the load, validate mode and registration (`422`, violation code `retired`). A `README.md` below the
root keeps the exclusions and payload rules that already covered it.

*Alternative rejected:* excluding the old files. They would vanish without a word, and the author would never learn
that the content moved.

### 6. The JSON form's `body` is the Markdown below the H1 (call 3, D4)

`GET /packs/{packId}/doc` with JSON answers `PackDocumentRead`: `title` is the document's H1 and is left out when
there is none; `body` is the preamble and sections in canonical Markdown, without the frontmatter and without the
title; `_links` holds `self` and `pack`. The Markdown form answers the held file byte for byte, frontmatter included.

*Alternative rejected:* `body` as the whole file. It would repeat the H1 that `title` already carries, and the raw
form already serves the whole file.

### 7. JSON is the default form, and a tie goes to the type listed first (call 4, D5, FR-024)

The form is chosen by Spring's producible-type matching over two handlers with disjoint `produces` (the spike's
pattern (b)): it ranks by quality, then specificity, and settles a tie by the type the client lists first in `Accept`.
No `Accept`, `*/*` and `application/*` answer JSON; `text/*` answers Markdown. An `Accept` that names neither form is
`406`, a `Problem` with no code. Spring does not treat `q=0` as a refusal; the contract says so instead of adding
negotiation code of the service's own.

*Alternatives rejected:* Markdown as the default (JSON is the service's default form everywhere else); "a tie goes to
JSON", the plan's first wording, which would need negotiation code of our own on top of Spring's (the spike showed
Spring answers the first listed, and the operator accepted that on 2026-11-11).

### 8. One strong `ETag` for both forms, with `Vary: Accept` (call 8, squad A4, FR-024)

Both forms carry the pack revision, quoted, as their `ETag`, as `getPack` answers it, and every `200`, `304` and `406`
carries `Vary: Accept`. `If-None-Match` is compared weakly (RFC 9110 section 13.1.2): `*` or a listed tag that matches
answers `304` with the tag and `Vary`, and a value that is not an entity-tag list matches nothing. The service answers
the `304` itself; Spring's implicit check is kept off the entity, because it scans a header for any quoted tag and does
not match `*` on a `GET`.

RFC 9110 prefers a distinct strong validator per representation. This is a deliberate call: the ruling names "the
pack revision as `ETag`", and the revision is what a client compares with `getPack` and sends as `If-Match` on a
write. `Vary: Accept` tells a cache that the representations differ, so a shared cache keys by both.

*Alternatives rejected:* a tag per form (for example the revision with a suffix), which breaks the "same revision"
comparison with `getPack`; no `304` at all (research D9), which the squad's A4 and the spike overturned.

### 9. The dedicated refusal is `pack_document_not_found` (call 5, D6)

A pack that holds no `README.md` is `404 pack_document_not_found`, a new refusal code. An unknown pack, and a
built-in pack, are `404 pack_not_found`, as `getPack` judges them, and a pack whose `pack.yaml` cannot be read is
`500 pack_unreadable`, as `getPack` answers it: both reads judge the pack through the same query
(`PackSummaryQuery.described`). A `README.md` the store refuses is `500 pack_unreadable`. The pack resource carries a `doc` link exactly when the pack holds a document, so following it
does not meet `pack_document_not_found`. The relation is `doc`, as the ruling names it, not the IANA `describedby`,
which points at a description of a resource's kind.

*Alternatives rejected:* `doc_not_found`. "Pack document" is the model's term. Serving a built-in pack's
`README.md` read-only at `/doc` was rejected too: the document read resolves the pack as `getPack` does
(`PackSummaryQuery.stored`), and a built-in pack has no registration summary, so `getPack` answers it
`404 pack_not_found`. A built-in `/doc` would answer a document whose `pack` link and shared `ETag` point at a read that
does not exist. Keeping `/doc` on the pack resource family keeps its `404` the same as the pack resource's: a built-in
pack has neither.

### 10. `active_charter` is refused by the closed descriptor (call 6, D7)

`active_charter` is removed from `pack-descriptor.schema.json` and from the domain (`PackPaths` is
`{packDrg?, mutabilityPolicy?}`). The descriptor is closed, so a `pack.yaml` that still declares the key is refused as
an unknown key: the pack reads as `pack_unreadable`, and a registration refuses it with `422 validation_failed` and one
`pack_unreadable` violation at `pack`. Both details name the undeclared key. The `org-charter` schema and the validate-mode `org_charter_schema_error`
finding are deleted.

*Alternative rejected:* a list of retired keys, beside the retired files. It is a second mechanism for what the
closed schema already does.

### 11. Readers read the aside copy during a swap, and re-read on a changed identity (research D8, squad D1, D4 and D7)

A replace is two renames: the live directory to `<name>.pack-old-<unique>`, then the staged copy into its place. A
reader takes no lock. It resolves the pack's entry: the live directory when it exists; when it is missing and exactly
one aside copy the service left sits beside it, the aside copy, which holds the last committed state; otherwise
nothing, and the read refuses as before. An aside copy beside a directory the service may not write is never read.

Because the aside copy is deleted right after the second rename, and a whole read can span both renames, a read runs
inside an identity-checked re-read (`PackDirectories.read`): the pack's count of directory moves is taken, the
directory is resolved and its identity taken (its path, its `fileKey` and its creation time, not its modification
time, which an edit in place changes), the read runs, and the directory is resolved and the count taken again. The
answer is kept only when both resolutions name the same directory with the same identity and the count did not move.
Otherwise the read runs again. Where the platform keeps no birth time (tmpfs, some overlayfs), `creationTime()` falls
back to the modification time, so there a file created in the pack counts as progress and the read runs again. It is
never accepted wrongly, because correctness rests on the `fileKey` and the count. The count (`PackWriteGeneration`
`moved`/`movesOf`) is recorded by the real swap after its first rename and before any rollback. It catches a swap whose
second rename fails: the aside copy is moved back, the same directory with the same identity, and a read that ran
between the renames found no pack (squad D-1, pre-merge). Only an attempt whose identity or count changed counts as
progress, so a pack that is unreadable for another reason refuses as before and is never retried. After 8 such attempts (one replace moves
the directory twice, so about four replaces) the read refuses `500 stale_input`, which the caller may retry, never
`pack_unreadable`.

An entry that is an empty plain directory is the reservation of a create in flight: it reads as no pack
(`404 pack_not_found`) and is not listed. The pack listing takes no read wait: it uses `tryLock` only, and lists a
half-swapped entry through the same resolution.

*Alternatives rejected:* a read/write lock (ruled out by #490: hot reads take no lock); a test-only retry (ruled out);
a re-read triggered by an exception only, which misses a torn read that lists the old tree and reads the new one
without failing (squad D1); an unbounded or unconditional retry, which only makes a flake rarer (squad D4); comparing
the write generation's value instead of a count of moves, which misses a rollback when the reader takes the generation
after the swap began and compares it again before the swap ends (squad D-1).

### 12. A verified read inside one swap is answered when its digests agree (call 9, FR-023, squad D3)

`ConsistentRead` refused `stale_input` whenever the write generation was odd, so every verified read answered `500` in
the window the #490 ruling closes. It now accepts a read that starts and ends in the **same** odd generation when its
digests before and after the load agree. The generation is read again after the second digest, so both digests fall
inside that one swap. A read that started in an even generation is judged as before. A load, or a first digest, that
fails inside a swap still refuses `stale_input`, as before: a failure there may be the swap's own.

It is sound because every pack write brackets its one directory swap with the generation, under the shared
`RegistryWriteLock` (ADR-0065 D12), so one odd generation moves the readable state from the old pack to the new once,
and never back. Two digests that agree at its start and end prove nothing moved between them, so the load in between
saw that one state. A swap whose second rename fails moves the state from the old pack back to the old pack; a load
or digest that ran between its renames is read again over the restored pack (decision 11), so none answers that
window. It is a separate, revertible change.

*Alternative rejected:* keeping the refusal, so that the read fix of decision 11 would still answer `500` on every
verified read in the window.

## Consequences

### Positive

- A real pack registers without hand edits once open-packs applies the two drafts of the mission
  (spec-kitty/spec-kitty-open-packs#241, spec-kitty/spec-kitty-open-packs#242). An author's notes register and are reported as
  not held, with the reason.
- A retired shape is refused by name, with the reason and where its content moved, never as `unmodelled`.
- A client reads the pack document as Markdown or JSON, caches it by the pack revision and finds it through a link.
- A read during a replace answers the old or the new state, without a lock and without a `500`.

### Negative

- The two forms of the document share one strong `ETag`, against RFC 9110's preference (decision 8).
- `file_not_held` is a new `DiagnosticCode` value: a generated client regenerates, and a client that read the absence
  of `diagnostics` as "the whole tree was stored" must read it.
- A pack stored before slice B with a retired file (`pack.md`, `PROVENANCE.md` or `org-charter.yaml`) cannot have
  its aggregate read until it is registered again. The reads of its descriptor, revision, document and of one kind's
  files still answer; the step contract item read answers `404 pack_not_found`; a read that composes the pack with the
  others (`GET /offering`, the activation and effective reads) answers `500 pack_unreadable`, whichever pack it was
  asked about. A stored `pack.yaml` that declares `active_charter` cannot be read, so every read of that pack's
  descriptor (`getPack`, `/doc`, the artifact listing and item reads) and every composed read answers
  `500 pack_unreadable`. Core still reads `org-charter.yaml` and `active_charter` (DIV-303) and has no reader
  of `README.md` as a document (DIV-302); it aligns through the generated client.

### Risks and known limits

- **Swaps never overlap.** The rule of decision 12 assumes one swap per odd generation. The registry lock guarantees
  it (ADR-0065 D12) in one replica (ADR-0061); a second writer outside that lock would break the proof.
- **A refusal inside any swap is still `stale_input`.** The generation is registry-wide. A load that refuses inside
  any odd window, for example `directive_not_found` for a directive of another pack, still answers `500 stale_input`
  rather than its own `404`. This is unchanged from before slice B. Whether such a read should be re-run after the
  swap, or answer its own refusal, is an open call for the operator; it is not decided here.
- **A link's aside copy.** A pack linked into the configuration root swaps beside its link's target. The aside
  fallback follows the link; an aside copy beside a read-only linked target is not read.
- **Retry cap.** A pack replaced more than about four times while one read runs answers `stale_input`. The caller may
  retry.
- **The open-packs drift watch goes red after merge.** The nightly watch (`.github/workflows/open-packs-drift.yml`,
  `OpenPacksDriftTest`) will fail and open an `open-packs-drift` issue until the two open-packs changes of the mission
  (spec-kitty/spec-kitty-open-packs#241, spec-kitty/spec-kitty-open-packs#242) land. This is expected, not a regression: the
  open-packs packs still ship the retired files this slice refuses.

## Alternatives considered

Each decision above names the alternatives it rejected. Two broader ones:

### Keep `pack.md` and add `README.md` beside it

A second document, or a reader of both, is the compatibility the charter rules out ("the charter's model is
canonical": no deprecation window, no old reader beside the new one).

### Register a pack with excluded files refused, and let authors delete them

Authors keep orientation notes in a pack. Refusing them makes every real pack fail on files that are not pack content.

## References

- Rulings: [#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466#issuecomment-6805477785), #451, #431,
  #490, #432; `docs/plans/next-slices-2026-11-11.md`.
- Mission: `kitty-specs/slice-b-real-packs-register-01M7649C/` (`spec.md`, `research.md` D1 to D9,
  `research/squad-plan-point.md`, `research/doc-read-spike.md`).
- Contract: `contracts/charter/paths/packs_packId_doc.yaml`; the fragments under `contracts/charter/changes/` dated
  2026-11-11 (pack document, document read, excluded files, org charter retired, reads during a replace).
- Code: `PackFileKind` (classification, the fallback, case variants), `WholePackStore` (excluded files, `file_not_held`),
  `PackDocumentController` and `PackDocumentETag` (the doc read), `PackDirectories` (the aside resolution and the
  identity-checked re-read), `ConsistentRead` (decision 12).
- How-to: [Enforce presets on an organization's projects](/guides/enforce-org-presets/).
