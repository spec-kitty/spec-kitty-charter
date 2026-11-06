# Research: Layer merge, pack registration and effective reads

Phase 0 findings. Each entry is given as Decision, Rationale and Alternatives considered. Code references are on
branch `issue-10-layer-merge-effective-reads`. `P/` stands for `service/packs/src/main/java/ai/speckitty/charter/packs/`
and `A/` for `service/activations/src/main/java/ai/speckitty/charter/activations/`.

The operator's rulings come in two groups:

- the six discovery decisions and four planning decisions in `decisions/`;
- the planning rulings of 2026-11-06 given with this plan's brief.

They are recorded here as decided. This document does not reopen them; it settles what they leave to design.

## R1. Registration is a full replacement (ADR-0065)

- **Decision.**
  - Registering a whole pack replaces the pack the service holds of that identity, removals included.
  - `PUT /packs/{packId}` takes an archive of the authored pack directory. `POST /packs` takes a source the service
    fetches (#374).
  - Any `pack_version` replaces. An identical re-send is a `200` no-op, and the same version with other content is
    `409 pack_version_conflict` (`DM-01M6R0TCAGHF27RKCB20QSCN8W`).
  - Only org and project packs are writable. Built-in is read-only and answers `409 registration_conflict`
    (`DM-01M6R1GQYFZ6Q6DFSWNDVTE9X1`).
  - A new ADR, **ADR-0065**, records the rule and supersedes ADR-0026 D2.
- **Identity.**
  - `{packId}` is the configured identifier, the directory name. Every pack-scoped path already names it
    (`HoldingPack.packId`, `PackCommitStore.resolve`).
  - The archive's descriptor `pack_id` must equal the stored pack's `pack_id`, and its `pack_level` the stored
    level. A mismatch is `422`, because a pack's identity and level are fixed for its id (spec Edge Cases).
  - A new pack's `pack_id` and `name` must be unused among the configured packs: `409 registration_conflict`, which
    the compose would otherwise answer later as `pack_duplicate_id` or `pack_name_conflict`.
  - For `POST /packs`, the descriptor `pack_id` finds the stored pack to replace. With none, the new directory takes
    the descriptor's `name`, which ADR-0053 d7 keeps unique and stable within a compose.
- **Rationale.** ADR-0048 d10 makes `pack_id` the identity and the pack the provenance. ADR-0038 keeps one directory
  per pack. The operator ruled full replacement.
- **Alternatives considered.**
  - A JSON aggregate body: ruled out in `DM-01M6R0TA37CQ0VEMHWSTJF2TRM`.
  - Refusing a replace that strands an activation's pin: ruled out; the read lists the id as stale.
  - Keeping ADR-0026's "no `DELETE`" with whole-pack registration as the only removal: ruled out (`DM-01M6R0TEM8YJK1J0DP0JSMSJNX`).
- **Two `@WIP` scenarios of `register-pack.feature` are superseded.**
  - PR-PACK-REGISTER-003 ("two sources claiming one pack id are refused") is superseded by FR-004 (replace).
  - PR-PACK-REGISTER-004 was already superseded by ADR-0053 d6.
  - ADR-0065 says so, and the rewritten file drops both.
  - The lockfile and the published-artifact kind (PR-PACK-REGISTER-001 and 002 in part) stay `@WIP` in a file of
    their own; this mission does not build them.

## R2. The effective set, computed from the merged closure

- **Decision.**
  - `packs` merges and `activations` decides.
  - `ClosureView.merged()` answers a `MergedClosure`, built lazily from the facts the verified read already holds:
    - the winner of each URN (configured `packId`, descriptor `pack_id`, `level`), with its mutability level along
      the closure;
    - the `applies_to_languages` of each winner;
    - the merged edges;
    - the layer-override records.
  - The adapter copies it into the activations value `MergedSnapshot`. `EffectiveSet` then computes, in this order:
    1. **Held.** The merged closure's activatable artifacts, one per id. A document id the closure does not hold is
       a stale id: it is reported and has no state.
    2. **Floor.** Two kinds of floor item, with different force (operator ruling on Q6; R11 has the detail):
       - **Locked** (a pack of the closure locks it, ADR-0012 D7): in force with reason `locked`, whatever the other
         rules say. A deny entry on it is listed in `denyNotApplied`.
       - **Required** (a governing preset of the closure lists it as must-have; FR-014, DIV-205 ratified,
         `DM-01M6R0TK6F4CC0Z00CZTS21V72`): a requirement, satisfied when the artifact or an active override of it is
         in force, never by an enhancement (Q8). The floor puts it in force only when nothing satisfies it.
    3. **Candidates.** A selected id must pass every filter, or it is not in force with the first reason that
       applies:
       - kind in `activatedKinds` (`kind_not_activated`; absent means none, ADR-0053 d10);
       - languages (`language_filtered`: an artifact with a non-empty `applies_to_languages` list none of the
         document's `languages` is filtered; an absent `languages` filters nothing; `[]` filters every tagged
         artifact);
       - mission type (`mission_type_not_in_force`, for a step contract or step enhancer whose mission type is not
         in `missionTypes`, ADR-0059 D2.2 step targets);
       - deny (`deactivated`, state `excluded`).
       An unselected artifact is `inactive` with `not_selected`.
    4. **CRG overrides.** An artifact is *active* when it is a candidate that passed every filter, or a locked
       artifact. Every target of an `overrides` edge from an active artifact is not in force, with reason
       `overridden` (`DM-01M6R0TGX3DSR54EVS58Q1V8FV`), except a locked target: it stays in force and the override is
       reported in `overrideNotApplied`. Overrides apply from the active set before any is removed, so a chain
       `F → E → T` hides both E and T, and no order of evaluation matters. A cycle among active artifacts refuses
       with `500 pack_unreadable`, naming the cycle; it never picks one. A required target hidden by an override
       is satisfied by it (R11).
    5. **`enhances`.** Nothing is added. An enhancer is in force only by its own route, and only while one of its
       targets is in force; otherwise it is `enhanced_target_not_in_force` (Q8, R11). The reads report the edge
       from both ends: the graph keeps `E enhances T` when both are in force, and the action context lists an active
       E with its T (FR-015, ADR-0059 amendment R4).
- **Rationale.**
  - C-002 and the operator's ruling place the merge in `packs` and keep `activations` on its port.
  - The algebra is the activation document's own rule set (ADR-0020 D6, D7, ADR-0013), so it is domain code of
    `activations`, run on values, as `DocumentRules` and `CascadeRules` are.
  - Laziness keeps `/offering` and the writes byte-for-byte unchanged (FR-023, SC-004).
- **Alternatives considered.**
  - Merging inside the compose resolver, so every caller sees it: rejected. It would change `/offering` and every
    write's refusals.
  - Computing the effective set in `packs` and handing `activations` a finished answer: rejected. `packs` would then
    read the activation document's rules, a dependency in the wrong direction.
  - A second walk in `activations` for the graph or the context: forbidden by C-002 (R8).
- **Open point.** The write cascade keeps walking the unmerged edge index (open item 3). The operator's ruling that
  the cascade stays write-only is honoured; whether it should follow merged edges is not ruled.

## R3. Layer overrides: grain, edges, mutability and how the records travel

- **Decision.**
  - **Grain.** Per URN, the version from the level that applies, in merge order (built-in, then org, then project),
    replaces the others whole (ADR-0059 D3, ADR-0053 d6).
  - **Sibling settlement first.** Within one level, a settled sibling contest puts the named member's version in
    force (#357, ADR-0053 d5). It is applied before the levels merge.
  - **Edges** (FR-010), over `EdgeIndex.Place(pack, urn)`:
    - an edge whose source place lost is dropped;
    - an edge whose target place lost is re-pointed to the winner's place, whether written bare or
      `<pack>::`-qualified.
  - **Mutability** (FR-011, ADR-0063 D3).
    - An override is allowed only when the overridden artifact's level is `open`. `extendable` permits enhancing and
      removal, not replacement (ADR-0012, ADR-0030 D3).
    - The level is `PerPackPolicy.levelIn(packs, urn)` over the closure's packs, the winning pack excluded. That
      counts the losers' own files and any pack that raised the level (ADR-0012 D3), and never lets the winner block
      itself.
    - A violation answers `500 pack_unreadable`, naming the artifact, both packs and the level.
  - **How the records travel.**
    - `LayerMerge` returns `LayerOverride(urn, winner(packId, level), loser(packId, level))`, one record per losing
      version.
    - `ClosureView.merged().layerOverrides()` carries them, in merge order, and the adapter copies them into
      `MergedSnapshot`.
    - `getEffectiveGraph` answers the records whose artifact is in force (FR-018). The provenance of every effective
      artifact comes from the same winners (`DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`).
    - A settled sibling contest is not a layer override and is not listed. The winner's provenance shows it.
    - No `Diagnostic` is widened, and no diagnostic list is shared by the reads (the typed option was ruled).
- **Rationale.** The records are a value of the merge, so they travel with its answer, and no second channel can
  disagree. Excluding the winner from the level check follows ADR-0012's purpose: a level protects the original from
  others.
- **Alternatives considered.**
  - A `DiagnosticCode` for layer overrides (an ADR-0059 follow-up): replaced by the typed list the operator chose.
  - Judging the level over the whole closure, winner included: rejected. A project that locks its own override would
    refuse itself.
  - Judging only the losers' own files: rejected. It ignores an org pack that locks a built-in artifact (ADR-0012
    D3).
  - Listing every override in the closure, not only those in force: rejected. The graph answers what is in force.

## R4. The archive revision and the version rule

- **Decision.**
  - A stored pack's revision is `PackRevision`, computed by `PackCommitStore.revisionOf`. It is the domain-tagged
    SHA-256 over the bytes of every file `PackFileSet.digested` lists, in its order. The staged archive gets the same
    computation, so the revision `PUT` compares, answers as `ETag` and takes in `If-Match` is the one every
    per-artifact registration answers.
  - The staged tree is stored as extracted: the load validates it but does not rewrite it. An identical archive
    therefore yields an identical revision, and the version rule compares like with like:
    - the same revision answers `200` and writes nothing;
    - the same `pack_version` with another revision answers `409`;
    - any other version replaces.
  - The digest takes file bytes in path order but not the paths themselves. Version v2 of the domain tag adds each
    pack-relative path as a length-prefixed field, so a renamed file with the same bytes is a different revision.
  - v2 is used for every revision from this mission on. An `ETag` is opaque to clients (ADR-0026 D5), so the change
    breaks no contract.
  - The load already refuses an unmodelled file, so every stored file is digested.
- **Rationale.**
  - `If-Match` from an artifact registration must match the whole-pack `PUT`, and the reverse. One function gives
    that.
  - Storing the bytes as authored keeps comments and order (ADR-0036).
- **Alternatives considered.**
  - Digesting the archive's bytes: rejected. tar and zip metadata (times, order, compression) change the digest for
    the same pack.
  - Re-serialising through `WholePackStore.write` and digesting that: rejected. A pack provisioned by copy would
    never match its own re-send.
  - Keeping v1, without paths: rejected. Two packs that differ only by a file's location would read as identical.

## R5. The effective-read `ETag`

- **Decision.** `EffectiveTag` = `sha256:` over the domain tag `charter-effective-read\0v1\n`, the activation
  revision and the verified fingerprint, as the closure read's `closureTag` (`A/application/ActivationService.java:292`)
  does with no tag. `304` on a weak match (`EntityTags.noneMatch`), as the closure read does. The tag version is bumped
  in any change that alters what the reads answer for the same inputs (a merge rule change, a new reason), so a
  strong tag never names two different bodies across releases.
- **Rationale.**
  - The answer depends on the document and on the packs, and on nothing else (ADR-0059 D5).
  - The query (`kind`, `state`, page, `depth`) selects a different resource, and an entity tag is per resource, so
    it needs no field of its own.
  - The domain tag keeps the effective reads' tags apart from the closure read's.
- **Alternatives considered.**
  - Reusing `closureTag` unchanged: rejected. A merge-rule fix in a release would keep the tag for a different body.
  - Digesting the response body: rejected. It costs a serialisation, and a `304` should not compute the body.

## R6. Inbound references on `DELETE`

- **Decision.**
  - `InboundReferences.of(packId, urn)` answers every configured pack other than this one whose references resolve
    to this pack's artifact. Its inputs come from one `ConsistentRead`: every configured pack's descriptor and
    composition facts (references, edges and preset items).
  - Resolution follows the compose's one rule, `PackCompositionResolver.holderOf`, each pack over its own
    `compositionClosure`:
    - a `p-name::urn` reference resolves to the pack of that name;
    - a bare one resolves to the referring pack, else to the first holder in its closure.
  - A descriptor `overrides` entry naming this pack as `member` for that artifact also counts.
  - The scan composes nothing. An unrelated sibling conflict or cycle does not block a delete that could repair it,
    but a pack that cannot be read fails the delete closed with `500 pack_unreadable`, naming it.
  - The answer is `409 artifact_referenced`. Its `packs` member names the referencing packs, at most 100.
  - The same scan serves a whole-pack replace's removed artifacts (Q3) and an in-pack referrer (Q2).
- **Rationale.** The operator ruled "refuse inbound references from other packs". Reusing `holderOf` keeps "who
  references what" identical to what the compose would later refuse with `pack_reference_artifact_missing`.
- **Alternatives considered.**
  - Composing every configured pack: rejected. A broken unrelated pack would block every delete.
  - Allowing the delete and letting the compose refuse later: ruled out in `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`.

## R7. Atomicity and concurrency

- **Decision.**
  - Every write (`PUT`, `POST`, both `DELETE`s) holds the pack's write lock (`PackWriteLocks`) from the `If-Match`
    check to the swap.
  - A replace stages a complete new tree beside the live directory and swaps it with `PackCommitStore`'s two-rename
    swap. The #281 listing repair already restores an interrupted swap.
  - A create uses `WholePackStore.write`'s reserve-and-move.
  - A reader is protected by `ConsistentRead`: a read that straddles a swap sees two digests and is refused
    `stale_input`, never answered mixed. The effective reads retry such a read up to three times
    (`ClosureQueries.snapshot`) before refusing.
- **Rationale.** FR-007 and NFR-006. The swap and the repair are the tested mechanism; a second one would split the
  crash story.
- **Alternatives considered.**
  - A domain-wide read–write lock: rejected. Readers would wait on uploads of unrelated packs, and the lock is
    in-process only (ADR-0061, one replica).
  - Copy-on-write versioned directories: deferred. It changes the pack domain's layout (ADR-0038).
- **Ruled (Q7).** A stale read is retried up to three times. A `stale_input` refusal is acceptable under NFR-006,
  because it never shows a mixed pack. The concurrency test fails on a mixed answer only, and reports the refusals and
  retries it saw.
- **Locking.** R-lock below replaces the per-pack-only scheme above for writes, and adds the write generation to
  the reads.

## R-lock. One registry write lock, a write generation, and no artifact-level mutex (locking research squad)

- **Finding.** Both lenses of the locking research squad converged.
  - **No artifact-level mutex.** The pack is the aggregate. The per-pack `PackWriteLocks` already serialise the
    `If-Match` check and the write inside one pack.
  - **The gap is the cross-pack check-then-act steps:**
    - the inbound-reference scan of an artifact `DELETE` and of a whole-pack replace (R6);
    - the uniqueness of a new pack's `pack_id` and name;
    - the write gate along the closure (ADR-0063 D3);
    - a relationship `PUT` that adds a cross-pack target (`ComposedRelationshipTargetCheck`);
    - two concurrent replaces, each composed against the other's old version.
- **Decision.**
  - **`RegistryWriteLock`.**
    - One fair `ReentrantLock(true)` for the whole pack domain, in `packs.application`.
    - Every pack write takes it first: the per-artifact registration `PUT`s and `POST`s, the relationship `PUT`,
      both `DELETE`s, `PUT /packs/{packId}` and `POST /packs`.
    - The per-pack lock is taken inside it, so the lock order is always registry, then pack.
    - Readers take no lock.
  - **Slow work outside the lock**, as `ToolguideRegistrationService` fetches before it commits: the archive
    download, the git fetch, the extraction and the staging. Under the lock run only:
    1. the load;
    2. the version rule;
    3. `If-Match` and `If-None-Match`;
    4. the gate;
    5. the compose;
    6. the inbound scan;
    7. the swap.
  - **A write generation (seqlock) in `ConsistentRead`.**
    - An `AtomicLong` that is odd during a swap and is bumped before and after it, by `PackCommitStore` and
      `WholePackStore.write` through an application-ring `PackWriteGeneration`.
    - A read records the generation before its first digest and checks it after its second. An odd or changed
      generation is `STALE_INPUT`. This closes the ABA hole, where a read spans A → B → A and both digests agree.
    - A first-digest failure while the generation moves (a directory renamed mid-listing) is `STALE_INPUT`, not
      `PACK_UNREADABLE`.
    - The content digest stays, for hand edits on disk that bypass the service.
  - **Retry (new work).** `ClosureQueries.snapshot` retries a `STALE_INPUT` read up to three times; it has no retry
    today. After that, `stale_input` is the answer, which is acceptable (Q7).
  - **API guarantees.**
    - Both `DELETE`s require `If-Match` on the pack revision (`428` when absent, `412` when stale), checked under
      the locks. They use a pack-specific parameter, `PackIfMatch`, not the activations' `IfMatch.yaml`.
    - `PUT /packs/{packId}` keeps `If-Match` optional, and accepts `If-None-Match: *` for create-only: `412` when
      the pack exists. `POST /packs` takes the same two headers. Both together is `400`.
    - An identical archive is a `200` no-op even under a stale `If-Match`. The version rule runs before the
      precondition, as RFC 9110 allows a server that sees the change already made.
    - The per-artifact registration `PUT`s keep `If-Match` optional. Their text says that, without it, the write is
      last-writer-wins on the whole pack.
  - **ADR-0061.** The scheme is in-JVM and so assumes one replica. ADR-0065 and the `RegistryWriteLock` javadoc
    note that it must be revisited with ADR-0061's cross-process lock item.
  - **The activations side.** A pack `DELETE` does not check activations. A dangling saved selection is reported in
    `staleIds` at read, and is dropped on the next write (ADR-0059 amendment R5).
- **Rationale.**
  - The races are between packs, so the lock that closes them must span packs. Every write is short once the slow
    work has moved out, so one fair lock costs little.
  - The seqlock catches what two equal digests cannot: a read that saw two different states which happen to digest
    alike.
- **Alternatives considered.**
  - An artifact-level mutex: rejected. It closes no cross-pack race, and the pack is the unit of consistency.
  - A read–write lock that readers take: rejected. Readers would wait on uploads, and the seqlock gives the same
    guarantee without blocking.
  - Ordered multi-pack locking (lock every pack a check touches): rejected. The scan and the gate touch every
    configured pack, so it degenerates into the registry lock, with a deadlock risk.
  - An unfair lock: rejected. A stream of per-artifact `PUT`s could starve a replace (the starvation test).
- **Tests.** Each runs 100 times, started together by a `CyclicBarrier`, with a 30 s timeout:
  - **mixed reads:** 8 writers cycle versions A, B and C (A → B → A included), against 8 readers, with marker files
    and an injected slow rename. No read answers a mix; a `stale_input` answer is allowed;
  - **delete against reference:** `DELETE A::X` races a relationship `PUT B → A::X`. Exactly one succeeds, and the
    configured packs still compose;
  - **create race:** 8 creates of one `pack_id` give one `201` and seven `409`;
  - **conditional replace:** replaces race `PUT`s with `If-Match`. The `ETag`s form one linear chain;
  - **starvation:** 16 per-artifact `PUT`s and one replace. The replace completes within a bound.

## R8. No shadow logic: what crosses the port

- **Decision.**
  - `AvailablePacks.session(...)` keeps its signature. `PacksSession` gains three methods:
    - `merged()`, a `MergedSnapshot`;
    - `graph(inForce)`, the merged edges among in-force URNs;
    - `actionContext(missionType, action, depth, inForce)`, the walk's listing.
  - `inForce` is a value set of URNs computed by `EffectiveSet`, so `packs` filters and walks, and `activations`
    decides membership.
  - ArchUnit's existing rule, that only `activations.infra.packs` names `packs.application`, keeps the boundary.
- **Rationale.** C-002, and the operator's ruling that `activations` builds the reads on its existing port.
- **Alternatives considered.** Handing `activations` the raw `EdgeIndex`: rejected, because it is a packs type and
  invites a second walk.

## R9. Fetching a source inside the SSRF guards

- **Decision.**
  - **HTTPS archive.**
    - `GuardedHttpsExchange` is extracted from `HttpsGuideFetcher`, unchanged in behaviour. Every guard of that
      class's javadoc stays:
      - absolute `https`, port 443 only, no user information, no IP literal, an exact allowlist;
      - global unicast addresses only (`AddressPolicy`);
      - `GlobalAddressResolverProvider` at connect, against rebinding;
      - no redirects but checked hops (at most 3);
      - one deadline;
      - `NO_PROXY`, no authenticator and no cookies.
    - It takes a body policy:
      - the guide policy: Markdown types, UTF-8, 1 MiB;
      - the archive policy: `application/gzip`, `application/x-gzip`, `application/zip` and
        `application/octet-stream` (the format is then sniffed from the magic bytes), no `Content-Encoding`, 32 MiB,
        streamed to a spool file.
    - Its own allowlist is `charter.pack-sources.https.allowed-hosts`, empty by default, so it refuses until
      configured.
  - **Git.** The process threat model is in plan.md, "The system `git` binary". The guards that keep git inside the
    same SSRF fence are:
    - the URL is parsed by `java.net.URI`, and its host is checked against `charter.pack-sources.git.allowed-hosts`
      by the same normalisation as `GuideAllowlist`;
    - the host is resolved by the service's resolver, and every address must pass `AddressPolicy`;
    - one checked address is pinned with `http.curloptResolve`, so git's own lookup cannot rebind;
    - `http.followRedirects=false` means a redirect cannot leave the checked host;
    - `protocol.allow=never` plus `protocol.https.allow=always` refuse every other transport;
    - the proxy variables are removed from the environment.
  - **Ref grammar.** A ref is either:
    - a commit id, `^[0-9a-f]{40}$` or `^[0-9a-f]{64}$`;
    - a `refs/heads/<name>` or `refs/tags/<name>`, where `<name>` matches `[A-Za-z0-9._/-]{1,200}`, never starts
      with `-` or `/`, and holds no `..`, `//`, `@{`, `.lock` suffix, trailing `/` or `.`, or control character.

    Anything else is `422` before a process starts.
  - **The commit.** The resolved commit (`git rev-parse HEAD`) is answered as `source.commit`, so a branch ref is
    recorded as the commit it pointed to (ADR-0005 D8, PR-PACK-REGISTER-002).
  - **Local path.**
    - `charter.pack-sources.filesystem.root` is unset by default, and the source is then refused.
    - The path given is relative. It is resolved and read through `toRealPath()`, and must stay under the root's real
      path.
    - Each file is copied through the archive rules: no links, limits, path checks.
- **Rationale.** The operator ruled reuse of the guarded fetcher, and ADR-0005 D8's fail-closed allowlist. Sharing
  one exchange means one place to fix.
- **Alternatives considered.**
  - The guide fetcher widened in place: rejected. Its Markdown and 1 MiB policy is part of its contract.
  - Letting git resolve and checking afterwards: rejected, because the time between the check and git's own lookup
    is open to rebinding.
  - Org- and repo-scoped allowlist entries (ADR-0005 D8's mixed granularity): deferred. The ruling is
    "allowlisted hosts only". PR-PACK-TRUST-005 stays `@WIP`.

## R10. Archives: one bounded, path-safe extractor

- **Decision.**
  - **Formats** (`DM-01M6R1GKE8TAG2QBJT5PAHJQ8E`). Both are accepted, chosen by `Content-Type`:
    - tar.gz through the JDK `GZIPInputStream` and Commons Compress `TarArchiveInputStream`;
    - zip through Commons Compress's `ZipFile` on the spooled body (operator, Q4).
  - **Limits.** At most 32 MiB of body (`413`, from `Content-Length` or the counting stream), at most 32 MiB
    expanded, and at most 10,000 entries (`422`). Each is counted by the extractor, never taken from a header.
  - **Path rules.** IC-03 lists them. Each entry name is checked after the library has applied PAX and GNU long-name
    headers.
  - **Steps.** Stage, then `WholePackStore.load`, then `PackCommitStore`'s atomic replace.
  - **Links.** Never materialised; a file is written with `CREATE_NEW` and `NOFOLLOW_LINKS`.
    - A tar link entry is refused by its type flag.
    - A zip entry is refused when `ZipArchiveEntry.isUnixSymlink()` holds or its Unix mode names any type but a
      regular file or a directory. There is no hand-written central-directory reader.
    - `ZipFile` reads the central directory. An entry whose local header disagrees with it is refused.
    - Only the `STORED` and `DEFLATED` methods are accepted, and an encrypted entry is refused.
- **Rationale.** NFR-004 and NFR-005. Counting rather than trusting headers is what CVE-2021-35517 and zip bombs
  teach.
- **Alternatives considered.**
  - One format only: ruled out (both).
  - `ZipInputStream`: rejected. It reads local headers, which can disagree with the central directory.
  - `java.util.zip` (the first planning default): replaced by the operator's Q4 ruling. It cannot see a link entry.
  - A hand-written central-directory reader beside `java.util.zip`: rejected with it.

## R11. "Required" is a requirement, and an enhancer needs its target (operator, Q6 and Q8)

- **The rulings, in the operator's words.**
  - Q6: "T wins when locked only. 'Required' means 'this artefact, or an override/enhancement must exist'."
  - Q8, which narrows Q6: "enhancers always require their T to be active in order to work. they do not stand on
    their own."
- **Decision.**
  - **Locked.** A locked `T` is always in force (reason `locked`).
    - An active `E overrides T` is not applied: `T` stays, and the pair is listed in `overrideNotApplied`. Overrides
      are not permitted on locked artifacts.
    - A deny entry on `T` is not applied (`denyNotApplied`, reason `locked`).
  - **Required.** A governing preset's must-have item `T` is a requirement, not a forced member. It is satisfied only
    by `T` itself in force, or by an in-force artifact that overrides `T`. An enhancement never satisfies it (Q8).
  - **Enhancers need their target.** An artifact that declares `enhances` is in force only when at least one of its
    targets is in force. Otherwise it is `inactive`, with the new reason `enhanced_target_not_in_force`, and its
    entry goes in the new `enhancedTargetNotInForce` bucket, naming the targets. It is not returned as in force.
    - A step enhancer is the exception: its target `action:<type>/<step>` is never an artifact in force, so it is
      judged by its mission type (rule 4, ADR-0059 D2.2).
    - With several targets, only the in-force ones count. The graph drops the edges to the others.
  - **The computation.**
    1. **Base set:** the selected ids within `activatedKinds`, the language filter and `missionTypes`, minus the
       applied deny entries, plus the locked artifacts.
    2. **Overrides:** the overrides from the base set apply. A locked target stays.
    3. **Requirement:** each required, not locked `T` is judged:
       - in force: reason `required`;
       - out of force, and an in-force artifact overrides it (whether `T` is `overridden` or `deactivated`): it
         keeps its state and reason, and is satisfied, with `satisfiedBy` naming the overriding artifact;
       - otherwise: put in force with reason `required`, and a deny entry on it is listed in `denyNotApplied`.
       The overrides of what this step put in force then apply once. They can only hide artifacts, and a required
       artifact they hide is satisfied by them.
    4. **Enhancer pruning:** an enhancer none of whose targets is in force leaves force. This repeats down chains of
       enhancers (`E` enhances `F`, which enhances `T`). It only removes, so it ends.
    5. **Withdrawn overrides:** an artifact removed in step 4 that also declares `overrides` has its overrides
       withdrawn, and steps 2 to 4 run once more without it. If that second run removes yet another overriding
       enhancer, the read refuses with `500 pack_unreadable`, naming the artifacts, rather than iterate (open
       review item 7).
    An `overrides` cycle among active artifacts refuses with `pack_unreadable`.
- **The FR mapping.**
  - **FR-013:** "plus the required floor" reads "plus every required artifact that neither it nor an active override
    of it satisfies". "Never minus deactivated" holds for a locked artifact, and for a required one no override
    satisfies.
  - **FR-014:** the floor is the governing presets of the whole closure (unchanged), each item a requirement as
    above, plus the locked artifacts.
  - **FR-015:** "an enhancement is returned only when it is itself active" also requires a target in force. The
    action context and the graph list `E` only with an in-force `T`, and an `E` that only enhances is never in force
    on its own.
  - **FR-016:** an active override hides a required `T` and satisfies it. It never hides a locked one.
  - **FR-017:** the skipped report gains `overrideNotApplied` and `enhancedTargetNotInForce`.
  - **Unchanged scenarios:**
    - US3 AS2: a denied required `D` that no override satisfies stays in force with reason `required`, and the deny
      is listed as not applied.
    - US3 AS5: an active `E enhances T` with `T` in force puts both in force. With `E` not active, `E` is not in
      force.
  - **A new scenario (US3, edge):** `E` selected, `T` out of force. `E` is `inactive` with
    `enhanced_target_not_in_force`.
- **The write cascade agrees.**
  - A cascading select of `E` already selects `T` (ADR-0059 amendment R4), so a cascaded `E` is normally in force
    with its `T`.
  - The rule bites only when `T` is out of force for another reason: denied (the cascade stops there), filtered by
    language, of a kind outside `activatedKinds`, hidden by an override, or never selected because `E` was selected
    without a cascade.
  - The writes do not refuse such a selection. The read reports it.
- **Contract.**
  - `required` is reworded: "a preset requires it or a pack locks it; a locked one is always active; a required
    one is active unless an active override of it satisfies the requirement".
  - `satisfiedBy` can only name an overriding artifact.
  - `ActivationSummary.required` is reworded the same way.
- **Rationale.** These are the operator's rulings. Splitting locked from required keeps ADR-0012's "locked cannot be
  replaced". Q8 makes an enhancement an addition to a target in force, never a stand-in for it.
- **Alternatives considered.**
  - The floor always wins (the first planning default): replaced by Q6.
  - An enhancement satisfies a requirement (the Q8 default): replaced by Q8.
  - A full fixed-point iteration: rejected. Steps 3 and 4 only hide or remove. Step 5's one rerun covers the rare
    artifact that both enhances and overrides, and a second change is refused rather than resolved by an arbitrary
    order.

## R12. A deny entry on a required artifact is accepted when an active override satisfies it (operator, Q9)

- **The ruling, in the operator's words:** "relax it, override only, assuming T is not locked (overrides not
  permitted on locked artefacts)".
- **Decision.** This mission relaxes the activation write rule of #398 and #399 (`DocumentRules.checkWrite`, the
  floor refusal):
  - A deny entry on a required, not locked `T` is accepted when an override of `T` is active in the document as it
    will be stored after the write, over the activation's merged closure.
  - "Active" is judged on that stored document: selected or on the floor, within `activatedKinds`, the language
    filter and `missionTypes`, not denied, and not itself hidden by an override.
  - It is computed by the same `EffectiveSet` function the reads use, so the write and the read cannot disagree.
  - An enhancement never satisfies. A locked `T` still refuses with `409 artifact_locked`.
  - Without such an override, the answer is `409 artifact_required` as today.
- **Scope of the judgement**, the same split as DIV-204 and squad finding F1:
  - `createActivation` and `replaceActivation` judge every deny entry of the whole document;
  - the per-artifact and preset writes (deactivate, apply-preset with `force`, select with `includeDeactivated`)
    judge only the deny entries they add.
  - A later write that removes the override (an unselect of `E`) is not refused. The stored deny entry then reads as
    `denyNotApplied` and `T` is back in force. The read reports it, and the writes keep judging only what they add.
  - `CascadeRules` is unchanged: a floor artifact is still never a cascading-deactivate candidate (F2). Only the
    named target's own deny entry uses the relaxed check.
- **Ports and C-002.**
  - `activations.domain.DocumentRules` owns the rule. It receives the override facts as the `MergedSnapshot` value
    (`overrides` edges among the winners, winners' kinds and languages, locks) from `PacksSession.merged()`. That is
    the same port and the same verified read as the rest of the write.
  - `merged()` is consulted only when a write adds a deny entry on a required artifact. Every other write is
    untouched by the merge.
  - If the merge refuses (a forbidden layer override), that write answers `500 pack_unreadable`, as the reads do.
- **Contract text:**
  - the `deactivateActivationArtifact` path description and its `409`;
  - the `createActivation` and `replaceActivation` descriptions (`paths/activations.yaml`,
    `activations_activationId.yaml`);
  - `responses/ArtifactPolicyRefused.yaml` (`artifact_required`: "unless an active override of it satisfies the
    requirement");
  - the `CharterRefusalCode` text of `artifact_required`;
  - a changelog fragment (`### Changed`: the write accepts more).
- **The record.** An **ADR-0059 amendment (R6, 2026-11-06)**: ADR-0059 D4 and its R5 amendment hold the write rules
  for deny entries, and R11's read rules amend D2 and D3. ADR-0065 is about pack registration and is not the place.
  DIV-204 is updated: core refuses no deny entry, and the service refuses one on a required artifact only when no
  active override satisfies it.
- **Rationale.** This is the operator's ruling. Judging the stored document after the write, with the read's own
  function, makes "accepted on write" mean "satisfied at read" for that document.
- **Alternatives considered.**
  - Leaving the write rule unchanged (the Q9 default): replaced by the ruling.
  - Judging on the document before the write: rejected. A replace that adds both the override and the deny entry
    would be refused.
  - Refusing a later unselect of the override: rejected. It reverses F1, under which per-artifact writes judge only
    what they add.

## R13. The in-force resolution is a stratified fixed point (A2, A3, A8; post-plan architecture squad)

- **Decision.** In force is computed in four strata, each a fixed point over the one below: (1) the candidate set
  (selected, within scope, minus deny entries, plus locked); (2) overrides, composed transitively top to bottom: in
  `F → E → T` only `F` stays, and `F` satisfies a requirement on `E` and `T`; a locked target stays and the
  override is reported; (3) the floor, iterated until no required artifact is unsatisfied on the final set; (4)
  enhancers, a least fixed point: an enhancer is in force only when its support chain reaches a non-enhancer in
  force. The resolution lives in `packs` (`InForceResolution`), since every stratum walks edges (C-002).
- **Rationale.** A single pass lets a later step undo an earlier judgement (review A2: `{U}` with `T` neither in
  force nor overridden). A greatest fixed point lets an enhancer ring hold itself up (A8).
- **Alternatives.** One pass with a re-run (the earlier R11 step 5): replaced; a cycle among required artifacts is
  now reported at read and refused at registration.

## R14. Registration is asynchronous and judged against its impact (A5)

- **Decision.** Registration answers `202` with a status URI and runs as a bounded job (IC-14). Before committing,
  it composes every closure that contains the staged pack, found by the reverse `depends_on` index, and refuses
  with the violation naming the packs (IC-15). The client learns completion by polling the status URI.
- **Rationale.** The impact check and a source fetch can take seconds to a minute; holding the request open ties a
  servlet thread and the client to the lock. A failure found at registration costs one author a `422`; found at read,
  it costs every activation that composes the pack a `500`.
- **Alternatives.** A push callback (webhook) to a client URL: rejected, a new outbound SSRF surface. One global
  compose (ruleset G8): rejected, it blocks an author over packs they cannot see.

## R15. Requirements only tighten, and only over held ids (A1, A10)

- **Decision.** The requirement of a preset id is the union of the must-have items of every governing copy across
  levels. A must-have item no pack in the closure holds is dropped from the floor and reported. Another pack's preset
  item naming an artifact counts as an inbound reference to it, so `DELETE` answers `409`.
- **Rationale.** Locks only tighten; requirements now match. An unheld URN in the floor produced a stale selection
  that the writes then treated as active.

## R16. Override and enhancement edges are judged at registration (A6, A8, A11, A12, A14, A15, B8)

- **Decision.**
  - Only an `open` artifact can be overridden, by a layer override or by an `overrides` edge alike (A6).
  - `enhances` cycles are refused, and an enhancer always links to a real (non-enhancer) artifact (A8).
  - An artifact that both enhances and overrides one target, and an `overrides` edge that resolves to its own source
    after re-pointing, are refused (A11, A12).
  - `enhances` is not a sibling conflict; a settlement names exactly one winner (A14).
  - A pack's policy locks artifacts of itself and the packs it builds on (`depends_on`, lineage), not unrelated packs
    in the same closure (A15); it never binds the pack itself (B8).
- **Rationale.** Each shape either had no stable answer at read or let one pack govern packs it does not build on.

## R17. Writes stay total; reads explain (A4, B1, B2, B3, B5, B6/B7)

- **Decision.**
  - A replace judges the floor only on the deny entries it adds and drops stale ids (A4).
  - A write drops an unsatisfiable version pin, reported in `dropped`; a per-pack `(version, revision)` history
    forbids reusing a version with other content; a read reports an unheld pin (B1).
  - Stale ids keep dropping on any write, reported (B2).
  - A removal judges references against the tree after the change and lists the referrers (B3).
  - `includeDeactivated` clears deny entries only on what the walk reaches and selects (B5).
  - `ActivationChange.warnings` predicts what the read will not put in force (B6/B7).
- **Rationale.** A GET then PUT of the same body must never fail without a pack change (invariant I5), and a pack
  upgrade must never wedge every per-artifact write.

## R18. Mission types, steps and agent profiles are artifacts like any other (A7)

- **Decision (direction).** They follow the same activation, override and enhancement rules as any artifact.
  `activatedKinds` is a remnant of core, not a domain rule of the charter service; the API and representation are
  revised if they fall short.
- **Rulings (2026-11-06; research/a7-kind-model-2026-11-06.md).** Q1 `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`: selecting a mission type cascades to
  its steps, which stay bound to it; W32 withdrawn. Q2 `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC`: mission types are deniable unless required or locked.
  Q3 `DM-01M6RCY40CQQTHC130RWF43W0D`: presets name any kind; one kind registry. Q4 `DM-01M6RCY8MYNGNPJ247M2Y4880N`: `activatedKinds` and `missionTypes` on writes are
  refused `422` now; stored documents upgrade to `schemaVersion: 2` on read. Built as IC-20.
- **Side finding.** The generated client turns an absent list into `[]`, so a GET then PUT changes the revision; a
  test pins it (FR-042).

## Operator rulings on Q1 to Q7 (2026-11-06)

1. **Q1: the scope of `listEffectiveArtifacts`.** ADR-0020 D7's shape is kept: every closure artifact with a
   `state`, and "in force" means `state=active`. The scenarios read "absent" as "not active", asserted on
   `?state=active` (IC-08, IC-09).
2. **Q2: another artifact of the same pack references the one deleted.** The delete is refused with
   `409 artifact_referenced`, naming the referring artifact (IC-06).
3. **Q3: a whole-pack replace removes an artifact another pack references.** The replace is refused with `409`,
   using the same scan as `DELETE` (IC-04, R6).
4. **Q4: zip.** Zip is read with Apache Commons Compress too, whose entry types detect links. There is no
   hand-written central-directory reader (IC-03, R10, plan.md supply chain).
5. **Q5: the writable root.** The shared `<config-root>/packs` directory is the writable root. The `pack.yaml`
   level must be org or project, and must equal the stored level on a replace (IC-04).
6. **Q6: locked and required under an active override.** A locked artifact wins over an override. A requirement is
   satisfied by the artifact, or by an active override of it. Q8 removed the enhancement from this rule (R11).
7. **Q7: `stale_input`.** A stale read is retried up to three times, and a `stale_input` refusal is acceptable.
   Artifact-level locking is not needed (R-lock).

## Operator rulings on Q8 and Q9 (2026-11-06)

1. **Q8:** an enhancer never satisfies a requirement, and an enhancer whose target is not in force is not in force
   (R11).
2. **Q9:** the write rule is relaxed in this mission, for an active override only, never for a locked artifact
   (R12, IC-13).

No question is open.

## Open review items (logged, not silent)

1. **Effective artifact content (US3 AS1, "with the project version's content").** `EffectiveArtifact` carries no
   content. The plan answers the provenance (`packId`, `level`), and the scenario reads the content from
   `GET /packs/{packId}/artifacts/{kind}/{artifactId}` of that pack. No body is added to the list.
2. **No `GET /packs/{packId}`.** `PUT` and `POST` answer `Location: /api/v1/packs/{packId}`, the resource `PUT`
   replaces. The `_links` carry `edit` (the `PUT`) and `artifacts`, so no link points at an undeclared read
   (ADR-0019 D3).
3. **Closed (`DM-01M6R4W1H9P3451V89KHP97XKM`).** The write cascade walks the merged edges once the merge exists
   (FR-038).
4. **FR-019 "names".** The effective context is what ADR-0018's walk reaches from the action node over the in-force
   merged graph, `depth` counting `suggests` hops, plus active enhancers. The plan reads "the step contract names"
   that way because the reserved contract's `depth` parameter assumes the walk.
5. **`mission_type_not_in_force`** is added to `EffectiveArtifactReason` beside `overridden`. FR-017's bucket needs a
   matching reason on the listed artifact.
6. **Relationship `DELETE` of a body-borne edge.** An edge written as a field of an artifact's own body answers
   `409 registration_conflict`, because removing it means re-registering the artifact.

7. **Closed (A11).** An artifact that both enhances and overrides one target is refused at registration (R16).

## Open risks

- **Extracting the guarded exchange** touches the most security-sensitive class in `packs`. Its existing tests must
  pass unchanged, and a security lens reviews the extraction on its own.
- **git version drift.** The floor is git 2.50.1 (C-008). An older or patched git that ignores `http.curloptResolve`
  would reopen rebinding. Mitigation: a version check at startup, and a test that the pin is honoured (a fixture
  host resolving to a refused address).
- **Async status is in memory.** A restart forgets pending registrations (R14). Re-sending is idempotent by the
  version rule.
- **Built behaviour changes (slice A0).** The rulings change #399 behaviour; they ship as PR A0, its own PR
  stacked on #399 before PR A (decision `DM-01M6RFGB9NVD9MDWZ6AP0PBYH7`; plan, Changes to built behaviour).
- **The git size watch is polled.** A fast object stream can overshoot the bound between polls. The overshoot is
  bounded by the poll interval and the deadline, and each blob is checked exactly when it is written to staging.
- **NFR-006 under load.** Three retries may not absorb eight writers hammering one pack. A `stale_input` refusal
  is acceptable (Q7), and the mixed-reads test reports the refusal rate.
- **One replica.** `RegistryWriteLock` and the write generation are in-JVM (R-lock). A second replica or a second
  process writing the pack domain voids them. Revisit with ADR-0061's cross-process lock item.
- **The merge on a write.** R12 makes a deny entry on a required artifact consult the merged closure, so a merge
  refusal now reaches that write as `500`. It reaches only that write.
- **Lock hold time.** The compose and the inbound scan run under the registry lock, so every pack write waits for
  the slowest of them. The starvation test bounds it, and moving more work out of the lock is the remedy.
- **The merge cost.** `LayerMerge` and the effective set run per read, without a cache. NFR-003's budget is
  report-only. A cache keyed by the fingerprint is the fallback, and is out of scope.
- **Contract breakage on 0.x.** Several changes break on a minor, under ADR-0022, each with a `### Breaking` entry:
  dropping `kind_default`, the new required members of `EffectiveArtifact` and `SkippedReport`, and the new required
  `layerOverrides`. Core is not yet a consumer (charter, epic #186).
- **Collisions with open PRs.** #377 (`DomainRepositoryService`, `PackCommitStore`), #390 (`FilesystemPackStore`,
  `hexagonal-service.md`), #393 (`ProblemHandler`) and #240 (graph-document edges, ADR-0064). #240 changes where
  edges are read, so IC-07's merged edges must rebase onto it if it lands first.
