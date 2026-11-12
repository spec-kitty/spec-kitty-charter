---
title: "ADR-0065: Registering a pack replaces the whole pack"
description: Registering a whole pack replaces the known pack, removals included; per-artifact writes are helpers; removal is by DELETE; registration is asynchronous and impact-checked.
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: Accepted; D1 and D12 amended 2026-11-11 (excluded files are not held; readers read the aside copy during a swap, #451, #490)
decision_date: '2026-11-07'
type: ADR
kind: ADR
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0012-pack-mutability-policy
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0026-write-surface-is-functional-registration
  - adr/0030-removal-is-distinct-from-deactivation
  - adr/0061-one-replica-per-deployment
  - adr/0063-pack-mutability-policy-file
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before changing a registration or DELETE path, the registration job, the impact check or the version history.
  - profile: pack-author
    guidance_text: Read this for what sending a pack does to the pack the service already knows, and how to remove one artifact or one edge.
  - profile: core-maintainer
    guidance_text: Read this for the write semantics a generated client sees, including the asynchronous status resource.
---

# ADR-0065: Registering a pack replaces the whole pack

## Status

**Accepted**, 2026-11-07. Decider: Stijn Dejongh (operator). The record is written before any code serves it, in the
mission that builds the layer merge and the whole-pack registration
(`layer-merge-and-effective-reads-01M6R0J6`, delivered as PR A). It records the operator's registration ruling and the
rulings of the phase A delta squad:

- `DM-01M6TQ62HXYF362GAFS2BZ3PGF`: a relationship `DELETE` removes only a registered edge;
- `DM-01M6TQ67C9ZTNGZB9CY28JS54F`: PR A is strict, and the repair writes of
  [#404](https://github.com/spec-kitty/spec-kitty-charter/issues/404) are out of scope;
- `DM-01M6TR1KDAJ64GMJD6AYNZKT59`: the runner's follow-ups (the artifact `DELETE` against authored edges, a pair held
  in both kinds of graph document, and the edge gate on a whole-pack replace);
- `DM-01M6RA8225EXG6GAT1MM2R9T6C` (A5): registration is asynchronous and impact-checked;
- `DM-01M6RA9BKJY7X9BFV93QW1KM1T` (B1): the version-to-digest history.

It supersedes ADR-0026 decision 2 and amends ADR-0005 decision 1, ADR-0019 decision 2, ADR-0063 and ADR-0064
decision 4, as [Amends](#amends) sets out.

### Amendment 2026-11-11 (#451, #490): excluded files, and readers during a swap

Made by slice B ([#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)) on the operator's rulings of
2026-11-11 on [#451](https://github.com/spec-kitty/spec-kitty-charter/issues/451) and
[#490](https://github.com/spec-kitty/spec-kitty-charter/issues/490); the calls beyond the rulings are
[ADR-0073](/adr/0073-real-packs-register-as-they-are/) decisions 4, 11 and 12. The decision text below is
unchanged, and D1 and D12 are annotated where they stand.

- **D1.** The service holds the staged tree **less the files it reports as not held**. A file that an exclusion of
  `file-kinds.yaml` names, or Markdown that no rule models, is removed from the staged tree before the revision is
  computed and the tree committed, and each is reported in the answer's `diagnostics` as a `file_not_held` warning
  naming the file and the rule's reason. A registration that differs from the stored pack only in such files ends
  `unchanged`.
- **D12, readers during a swap.** A reader takes no lock. Between the swap's two renames it reads the single
  `<name>.pack-old-*` aside copy, which holds the last committed state, and it re-reads when the directory's identity
  (path, `fileKey`, creation time) changed under it, at most 8 times before `500 stale_input`. A verified read that
  starts and ends in one odd write generation is answered when its digests agree. An empty reserved directory reads as
  no pack, and the listing does not wait on a held lock.

## Context

[ADR-0005](/adr/0005-service-registers-and-distributes-packs/) made whole-pack registration the way content enters the
service. [ADR-0026](/adr/0026-write-surface-is-functional-registration/) then added per-artifact and per-relationship
registrations and said there is no `DELETE`: removing an artifact from force is deactivation. The result has gaps:

- a pack can only grow. Registering a pack again does not drop what the author removed, and nothing removes a stored
  artifact or edge, so a pack author cannot make the service hold what the author's repository holds;
- two sources can claim one pack id, and the scenario that refuses it
  (`register-pack.feature`, PR-PACK-REGISTER-003) leaves the author no way to move a pack;
- a registration is judged against the pack alone. A pack that other packs build on can be changed so that a
  closure, or a stored activation, no longer composes, and the failure appears later as a `500`;
- the write that reads a source (a download, a git fetch) is slow, and a synchronous request holds a connection and
  the registry open for as long as the source takes.

Relationships changed under it. The ADR-0064 record made the graph documents of a pack the only source of
relationships (decisions 1 and 2: no artifact field mints an edge) and gave one operation, `putPackRelationship`, the
write of an edge (decision 4). A whole-pack replace writes graph documents, and a `DELETE` removes registered edges,
so decision 4 needs amending.

## Decision

### D1. A whole-pack registration fully replaces the known pack

`PUT /packs/{packId}` (and `POST /packs`, for a source) leaves the service holding exactly the pack that was sent,
removals included: an artifact, an edge, a preset item or a file the new pack does not carry is gone. There is no
merge with the stored pack. The per-artifact and per-relationship registrations stay as **helpers** for a small
change; they are not the way to bring a pack to a known state.

The write is atomic: the staged tree is swapped in by a two-rename swap under the write generation, or nothing
changes. A registration of a pack that does not exist yet creates it.

*(Amended 2026-11-11, #451: the service holds the pack that was sent less the files it reports as not held, each a
`file_not_held` diagnostic. See the amendment under Status.)*

### D2. The version rule

- Any `pack_version` replaces the stored pack, an older one included. The version is a label, not an order.
- A re-send of the same content (the same revision) is a no-op: the job ends `succeeded` with outcome `unchanged`.
  This holds for the `If-Match` check under the lock, because the version rule comes before it. A stale `If-Match` is
  already a synchronous `412` at acceptance (D8), before any job exists, so an identical re-send under a stale
  `If-Match` is `412`, not `unchanged`.
- The same `pack_version` with another revision is `409 pack_version_conflict`.
- `If-Match` against the pack revision, or `If-None-Match: *` against its absence, is checked at acceptance and again
  under the lock (`412` when moved).

This replaces the "two sources claiming one pack id are refused" scenario: a second registration of a pack id
replaces, under the identity checks of D3. PR-PACK-REGISTER-004 is already superseded by ADR-0053 decision 6.

### D3. Only org and project packs are writable

The `pack_level` of the staged pack must be `org` or `project`; built-in is read-only (`409 registration_conflict`).
On a replace the level and the `pack_id` must equal the stored pack's (`422`). For a new pack, no configured pack may
hold its `pack_id` or its name (`409`). The shared `<config-root>/packs` directory is the one writable root.

### D4. Removal is by `DELETE`

Two `DELETE` operations remove content without re-sending the pack. Both are synchronous: they read no source and do
one bounded compose. Both require `If-Match` on the pack revision (`428` when absent, `412` when stale), and both run
entirely under the registry write lock and then the pack's lock (D12).

- **An artifact `DELETE`** removes the artifact and its companion files, its outgoing edges from
  `registered.graph.yaml`, and every preset item that names it (a preset key left empty is deleted, since every preset
  list needs at least one item). It is refused with `409 artifact_referenced` while another artifact or pack still
  refers to it, found by the inbound scan (edges, preset items, descriptor `overrides` entries, resolved through the
  referring pack's closure as it will be; the data-only fields of ADR-0064 decision 11 do not count). It never
  rewrites an author file: when a source shard or `drg/fragment.yaml` writes an edge from the artifact or declares its
  node, the answer is `409 registration_conflict` with the fixed reason "written in the pack's graph documents:
  register the whole pack" (`DM-01M6TR1KDAJ64GMJD6AYNZKT59`). A refused `DELETE` changes nothing: no preset item, edge
  or file is removed (FR-007).
- **A relationship `DELETE`** removes the edge of one unordered `{from, to}` pair, **only from
  `registered.graph.yaml`**, and answers `204` (`DM-01M6TQ62HXYF362GAFS2BZ3PGF`). It answers `404
  relationship_not_found` when no graph document of the pack holds an edge on the pair and no derived relation joins
  it. It answers **`409 relationship_not_registered`** when the pack holds an edge on the pair that is not in
  `registered.graph.yaml`: an authored edge (a source shard or `drg/fragment.yaml`) or a derived relation (a guard's
  `implemented_by`, ADR-0064 decision 7). When the pair is held both in `registered.graph.yaml` and in an author
  document, the answer is the same `409` and nothing changes, because a `204` that left the edge in force would
  mislead (`DM-01M6TR1KDAJ64GMJD6AYNZKT59`). A `DELETE` never rewrites a file the author wrote.

An artifact `DELETE` and the removals of a whole-pack replace go through the same write gate and the same inbound
scan, so a removal means one thing on either path. A whole-pack replace that removes a referenced artifact is `409
artifact_referenced`, answered before the compose.

A `DELETE` does not consult activations. A saved selection of a removed artifact reads as stale and is dropped on the
next write. A `DELETE` keeps the version history (D9).

### D5. The write gate gains a REMOVE operation, and runs over a staged closure

The operations the mutability gate judges are `REGISTER`, `REPLACE` and `RELATIONSHIP`; `REMOVE` joins them. It is
allowed on `open` and `extendable` and refused on `locked` (`409 governance_policy_violation`), as ADR-0012 decision 1
and ADR-0030 decision 3 say. It is used by the artifact `DELETE` and by the removals of a whole-pack replace, the
latter judged over the diff of artifacts added, changed and removed.

A registration writes a pack that is not (yet) in the live closure, and a replace may change its `depends_on`, so the
gate also runs over the closure composed with the staged pack in place. ADR-0063 decision 3 (the highest level along
the closure wins) is unchanged; only the closure it is asked of moves. A pack's own policy never binds it: the gate
refuses only when an ancestor of the written pack holds the artifact and locks it (or makes it `extendable`, for a
`REPLACE`). A pack that registers or removes its own artifact is never refused by its own policy.

The gate also judges the edges a whole-pack replace writes or drops, as the relationship operation does today
(`DM-01M6TR1KDAJ64GMJD6AYNZKT59`).

### D6. The impact check

Before a registration or a `DELETE` commits, the service composes every closure that contains the staged pack, with
the staged pack in place:

1. the closures found from the reverse `depends_on` and lineage index, rooted at each pack whose closure holds the
   staged pack;
2. the root set of each **stored activation** whose closure holds the staged pack (an activation with no `packs`
   roots at every configured pack), asked through the `ActivationImpact` port that `packs` declares and
   `activations.infra` implements, and composed once per distinct root set. This catches two packs that compose alone
   and conflict together because one activation lists both.

It judges the compose rules and the mutability rules, an `overrides` edge onto a target that is not `open`
(`422 override_not_permitted`), an `enhances` cycle or an enhancer with no route to a non-enhancer
(`422 enhances_cycle`), an artifact that both enhances and overrides one target (`422 conflicting_relations`), an
`overrides` edge that resolves to its own source (`422 self_override`) and an `overrides` cycle
(`422 override_cycle`). The refusal names the root pack of the closure and the packs involved. The relation checks
read the edge index or the facts' edges, never the graph walk, which sees only derived relations. On success, the
status lists the must-have items and locks added and removed.

Packs stored before this rule are not re-judged. A read that meets one of these shapes answers `500 pack_unreadable`
naming the pack, and the startup census logs them.

### D7. PR A stays strict

A registration or a `DELETE` whose resulting closures fail to compose or break a rule is refused, even when the write
would repair a closure that is already broken (`DM-01M6TQ67C9ZTNGZB9CY28JS54F`). The 2026-11-06 repair-write
amendment to ADR-0063 (refuse only new violations, [#404](https://github.com/spec-kitty/spec-kitty-charter/issues/404))
is **not applied** in PR A and stays as a tracked follow-up.

### D8. Registration is asynchronous

`PUT /packs/{packId}` and `POST /packs` run the synchronous checks (access, `charter.registration.enabled` and the
source kind's own flag, the `packId` grammar, body size, media type and magic bytes, `If-Match` and
`If-None-Match: *`) and answer `202 Accepted` with `Location: /api/v1/pack-registrations/{registrationId}` and a
`PackRegistrationStatus` body.

- A bounded executor runs the job: two running, eight waiting, and the next request is `503` with `Retry-After`.
- `GET /pack-registrations/{registrationId}` answers `pending`, `running`, `succeeded` (with the `PackRegistration`,
  the notices and a link to the pack) or `refused` (with the `Problem` a synchronous API would have answered). The
  `409` and `422` answers of D2 to D6 are the `problem` of a `refused` status, never a synchronous answer. The job
  also ends `refused` with `registration_timed_out` (`504`, at the source deadline plus 30 seconds) or
  `pack_storage_full` (`503`, less than the per-job peak free, checked at admission and again before anything is
  written; see the note of 2026-11-08 below).
- Status is kept in memory for one hour after the job ends, at most 1,000 entries. A restart forgets pending jobs and
  their URI then answers `404`; a client re-sends, which the version rule of D2 makes idempotent.
- The client polls the URI. There is no outbound callback, so the service opens no connection to a client-chosen
  address (SSRF).

**Note, 2026-11-08 (#418, items A3, A4, A6, A8).** The upload path of `PUT /packs/{packId}` changed; the decision above
stands, and the clauses it names are read as follows.

- *The body is streamed.* The archive is copied from the request stream, behind the 32 MiB counter, into an owner-only
  spool file in the job's directory. It is never held in the heap (A3).
- *A slow upload holds no slot (A4).* Two designs were open: (a) take the slot only after the body is spooled, or (b)
  count uploads in progress separately (two reading, `503` beyond) with a body read deadline. (a) is chosen, with a plain
  cap on the uploads open at once (see the cost below) instead of (b)'s body read deadline. That deadline needs a
  deadline on a blocking socket read, which the application cannot enforce between bytes, and an answer for an upload
  that outran it, and the contract declares no `408` or `504` for `registerPackFromArchive`; (a) answers only with
  statuses the contract already declares. Admission still refuses, before the body is read, a declared length over 32
  MiB (`413`), a full queue (`503` with `Retry-After`), a stale `If-Match` or an `If-None-Match: *` over a stored pack
  (`412`), and a full volume (`503 pack_storage_full`); the queue and the preconditions are judged again when the body is
  spooled, because both move during a transfer. The slot, and the 90 second deadline, start then, so the deadline no
  longer counts the transfer. The cost, and its bound: bodies arriving hold no slot, so they would be limited only by
  the server's connections (8192 with virtual threads), each writing up to 32 MiB that the others' free-space checks
  never saw. They are therefore counted apart from the queue: at most `RUNNING_LIMIT + WAITING_LIMIT` = 10 uploads are
  open at once, the eleventh is refused at admission with the `503` and `Retry-After` of a full queue (the same
  declared answer, no new code), and an upload gives its place back when its body is spooled and a reservation adopts
  it, or when it is discarded (a refused or abandoned transfer). An open upload still holds no queue slot. The
  free-space check at admission is cumulative: it asks for its own room on top of the declared length (32 MiB when none
  is declared) of every upload already open. Each body is bounded at 32 MiB, an abandoned spool is a job-id-named
  directory the sweep removes after 15 minutes (never one whose transfer is open), and the endpoint needs the worktree
  token and the flag. A job the deadline ends while it is reserved, before the controller starts it, is answered by its
  own end: `202` with the refused status `registration_timed_out`, the outcome the audit records, not a `500`.
- *Free space (A6).* The check runs at admission, from the declared length (a body with none is judged as full-size) plus the declared
  bytes of the uploads already open, and
  when the job runs, on the staging volume as well as the pack volume. The margin is the per-job peak: about three times
  the archive bound, the archive, the extracted tree and the copy the commit makes beside the pack (96 MiB for a
  full-size archive on one volume; on two volumes, the staging volume needs the archive and the tree and the pack volume
  the copy). A zip is read where it was spooled, not copied to a second spool, which is what keeps the peak at three.
  When the job runs the archive of an upload is on the volume already, so the run-time check asks only what is left to
  write, the tree and the copy: two bounds on one volume, a bound on each of two. A job that has still to fetch its
  archive (a source) is asked the whole peak. The per-job peak is the three together, the archive counted at admission.
  The refusal and its code are unchanged.
- *Staging area (A8).* The staging directory's lifecycle (root, owner-only job directory, deletion) is the application
  port `StagingArea`, implemented in `infra.storage` and composed in the deployable.

### D9. The version-to-digest history

`<config-root>/packs/.history/<packId>.json` holds a list of `(pack_version, revision, registeredAt)`, written in the
same swap as the pack. A registration whose version appears in the history with another revision is `409
pack_version_conflict`, whether or not that version is the stored one. A `DELETE` of an artifact or an edge keeps the
history.

### D10. Three source kinds, and their host requirements

A `POST /packs` names one source:

- **An HTTPS archive** (`https_archive`): a host on the exact allowlist `charter.pack-sources.https.allowed-hosts`,
  global unicast addresses only, no redirect except through checked hops, one deadline, no proxy, no credentials.
  `application/gzip`, `application/zip` and `application/octet-stream` (sniffed from the magic bytes) are accepted. A
  `sha256` in the request must match the body (`422 pack_source_refused`).
- **A git repository**: an HTTPS URL on its own allowlist and a ref, fetched by the system `git` binary through a
  fixed argument list with no shell, hooks off, no submodules and no LFS: a bare `init`, a shallow `fetch` into a
  private refspec, then the tree read with `ls-tree` and `cat-file`. Only regular-file and directory modes are
  accepted. A work tree is never checked out, and JGit is not used. The resolved commit is answered in the
  registration. The host requirement is the system `git` at version 2.50.1 or later, checked at startup; below 2.50.1 or without the binary the git source is disabled and answers
  `503 pack_source_unavailable` (NFR-011).
- **A local path**: relative to `charter.pack-sources.filesystem.root` and confined to it, read without following
  links.

The service-stored kind of ADR-0005 decision 1 is `PUT /packs/{packId}` with the archive as the body. The
published-artifact kind (`pkg:charter/<id>@<version>`) is deferred.

A source kind is off unless `charter.pack-sources.<kind>.enabled` is set; the git allowlist is empty by default; a
disabled kind answers `503 pack_source_unavailable`.

### D11. The archive limits

Every archive, whatever its source, goes through one bounded, path-safe extractor into a fresh staging directory:

- the body is at most 32 MiB (`413` before reading), the expanded bytes at most 32 MiB, and at most 10,000 entries
  (`422`). At most 32 MiB + 64 KiB of decompressed bytes are read before the refusal (NFR-004). The expanded bytes are
  counted while streaming; the zip entry count is read from the end-of-central-directory record before any entry is
  read;
- only regular files and directories are accepted; a symlink, a hard link, a device, a FIFO or a sparse file is
  refused, as is an absolute path, a drive letter, a backslash, a NUL, a `.` or `..` segment, an empty segment, a
  path over 4,096 bytes, a segment outside the grammar `[A-Za-z0-9._-]` (NFR-005), a name that collides with another after NFC and case folding, and an encrypted zip entry;
- entries are written with `CREATE_NEW` and no link following, files `0600` and directories `0700`;
- the extractor uses Apache Commons Compress for tar and zip. Its only accepted zip methods are `STORED` and
  `DEFLATED`.

### D12. Write coordination: one registry lock, then the pack's lock

The slow work runs first and outside every lock: the download, the extraction, the staging, the load and the
revision. A staging sweep leaves no staging directory older than 15 minutes (NFR-009). The decisive steps (the identity check, the version rule, the precondition, the gate, the inbound scan, the
impact check and the commit) then run under one registry write lock, taken with a 5 second `tryLock` (else `503`),
and then the pack's own lock, always in that order. Every pack write takes the registry lock: the per-artifact writes,
`putPackRelationship` (so a relationship check cannot race a `DELETE`), the whole-pack registrations and both
`DELETE`s. A write generation, odd for the duration of the swap, lets a reader detect a mixed answer.
*(Amended 2026-11-11, #490: a reader during the swap reads the aside copy without a lock, and a verified read inside
one odd generation is answered when its digests agree, ADR-0073 decisions 11 and 12. See the amendment under Status.)*

The locks and the generation are in-JVM and assume **one replica**
([ADR-0061](/adr/0061-one-replica-per-deployment/)). This is revisited with ADR-0061's cross-process lock item.

### D13. Registration has no principal

The service has no caller identity beyond the worktree bearer token, which the worktree guard checks before routing
(a missing, malformed or wrong credential is `401`, and a `401` is never the registration's own answer). Registration
and both `DELETE`s need that token, plus `charter.registration.enabled` (default `false`), plus the source kind's own
flag. Under the development flag no token is configured; the flags still gate registration. Every attempt writes one
audit record through an `AuditLog` port; the record names the worktree identity the guard knows and not a principal.
The guard also audits a `401` on the registration and `DELETE` paths (`DM-01M6TR1KDAJ64GMJD6AYNZKT59`, item 5): a
refused credential leaves a record although the registration never starts. The identity is the service's worktree id
(`ServiceInfoQuery.worktree()`, configured or derived), which is known with or without a token. Under the development
flag the record names that worktree id and states that the request carried no token; it never holds a token value.

## Amends

Each note keeps the decision it amends and annotates the point it changes.

### ADR-0026, decision 2: superseded

ADR-0026 decision 2 ("No `DELETE`, `PATCH` or generic CRUD. Removing an artifact from force is deactivation") is
**superseded** by D4 above for `DELETE`. There are two `DELETE` operations (an artifact, a registered relationship)
and the removals of a whole-pack replace. `PATCH` and generic CRUD stay out. Deactivation stays an activation concern
(ADR-0013, ADR-0020); a `DELETE` removes content from a pack its author owns, which is not a deactivation. The
operator's registration ruling is the authority. Decision 1.1 (register a pack) now has the replace semantics of D1.

### ADR-0005, decision 1: source kinds built

Of the four source kinds, three are built as D10 sets out: an HTTPS archive, a git repository and a filesystem
location. The service-stored kind is `PUT /packs/{packId}`. The published-artifact kind is deferred. ADR-0005 decision 1
does not decide the registration answer; the old `201 Created` comes from ADR-0019 (see below).

### ADR-0019, decisions 2 and 5: the table and the status code

The old `201 Created` answer comes from ADR-0019: its decision 2 table row for `POST /packs` and its decision 5
("Registration answers `201 Created` with a `Location` header"). Registration is asynchronous (D8), so a pack
registration answers `202 Accepted` with a status resource, and `201 Created` is no longer its answer. The table of
decision 2 is amended: `PUT /packs/{packId}` (replace a whole pack; `202`) and `DELETE
/packs/{packId}/artifacts/{kind}/{artifactId}` and `DELETE /packs/{packId}/relationships` join it, `POST /packs`
answers `202 Accepted` and not `201 Created`, and `GET /pack-registrations/{registrationId}` is a new top-level status
resource that is not a sub-resource of a pack.

### ADR-0030: derived-pack removal is untouched

ADR-0030's removal by a derived pack stands. It is a removal **across packs**, one artifact at a time in the derived
pack's own configuration, recorded in a policy file; this record's removal is the pack's own author removing the
pack's own content. The two are distinct, and the REMOVE operation of D5 is the one rule they share: allowed on
`open` and `extendable`, refused on `locked` (ADR-0030 decision 3). ADR-0030 gains no text change; this note is the
cross-reference.

### ADR-0063: the REMOVE operation, and the repair-write amendment is not applied

The write gate gains a REMOVE operation and a variant that judges over a staged closure (D5). Decision 3 (the highest
level along the closure wins) and decision 4 (an invalid policy file makes the pack unreadable) stand. The
"Negative" consequence ("a closure that cannot be composed refuses the write") stands and is the strict rule of D7.
The 2026-11-06 amendment (repair writes, #404) is **not applied in PR A**.

### ADR-0064, decision 4: more than one operation writes an edge

ADR-0064 decision 4 says `putPackRelationship` is the only operation that writes an edge and that an artifact
registration writes the artifact's own content only. It is amended:

- a whole-pack `PUT` writes **graph documents**: the author's shards and `drg/fragment.yaml`, replaced whole.
  `registered.graph.yaml` is included in the replace only when it carries the registration stamp
  (`generated_by: charter-registration`); an unstamped one is a reserved shard and is refused;
- the relationship `DELETE` removes a **registered** edge, from `registered.graph.yaml`, and from no other file;
- `putPackRelationship` remains the only operation that writes one edge into a pack without re-sending the pack.

ADR-0064 decisions 1 and 2 stand: no artifact field mints an edge, and the author's graph documents remain the home
of an authored edge. The exact note to append to ADR-0064 is in the hand-off of the work package, and is applied when
this record is merged with the branch that holds ADR-0064.

## Consequences

### Positive

- A pack author can make the service hold exactly what the author's repository holds, and move a pack between sources.
- A registration cannot leave a stored activation or a dependent pack unable to compose: the impact check refuses
  first.
- A slow source no longer holds a connection or the registry; a client polls one resource.
- A removal means one thing on the `DELETE` and the replace paths, and never rewrites an author's file.

### Negative

- A registration composes every closure the pack joins, and each distinct stored activation root set, under the
  registry lock. Registrations and `DELETE`s are rare, and the lock budget bounds the work.
- A client must poll. A restart forgets pending jobs.
- Strictness (D7) means a broken pack cannot be repaired by a write that leaves any closure broken, until #404 lands.
- The extractor and the git runner are a security surface: new dependency, host binary and crafted-archive corpus.

### Risks

- One replica only. Two replicas would break the locks and the generation until a cross-process lock exists.
- The checks run at registration only. A pack stored earlier can still be unreadable, and the read says so.

## Alternatives considered

### Merge a registration into the known pack

Rejected. The author could never remove content by registering, and the stored pack would drift from the source.

### Synchronous registration

Rejected. A source fetch can take as long as its deadline, holding a connection and the registry. `202` with a status
resource bounds both, and the executor's limits give back-pressure.

### JGit for the git source

Rejected. It would bring the library's own network and resolver stack into the JVM, where the service cannot pin the
checked address. The system `git`, run through a fixed argument list, with the address pinned, is the smaller surface
(git is a host binary, C-008).

### Remove an authored edge by rewriting the author's file

Rejected (`DM-01M6TQ62HXYF362GAFS2BZ3PGF`). The author's files are the author's; a service that edits them hides a
change from the author's repository. The answer is `409 relationship_not_registered`, and the author re-registers the
pack.

### Apply repair writes in PR A

Rejected for this record (`DM-01M6TQ67C9ZTNGZB9CY28JS54F`). The strict rule is simpler to prove, and the repair
semantics are tracked in #404.

## References

- [ADR-0005](/adr/0005-service-registers-and-distributes-packs/), decision 1: the source kinds (it decides no
  registration status code).
- [ADR-0012](/adr/0012-pack-mutability-policy/), decisions 1 and 3: removal and the levels.
- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/), decisions 2 and 5: the resource map and the registration
  status code.
- [ADR-0026](/adr/0026-write-surface-is-functional-registration/), decision 2: no `DELETE`.
- [ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/), decision 3: removal by a derived pack.
- [ADR-0061](/adr/0061-one-replica-per-deployment/): one replica per deployment.
- [ADR-0063](/adr/0063-pack-mutability-policy-file/): the policy file and the write gate.
- ADR-0064, decisions 1, 2, 4, 7 and 11: graph documents are the only source of relationships.
