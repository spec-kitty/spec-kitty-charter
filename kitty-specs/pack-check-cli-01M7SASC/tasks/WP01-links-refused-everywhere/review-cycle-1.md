---
affected_files: []
cycle_number: 1
mission_slug: pack-check-cli-01M7SASC
reproduction_command:
reviewed_at: '2026-11-19T09:57:16Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP01
---

# WP01 review feedback (cycle 1): fold the write-path reads, then resubmit with the profile

**Code verdict on the read surfaces: pass.** The independent review (reviewer-renata) found nothing at MEDIUM or above:

- every probe on the walk, census, StoreRead, inspector, digest and fingerprint goes through `PackFileSet.unlinkedFile`;
- there is exactly one `pack_entry_linked` per link;
- golden diffs touch only the link subjects;
- removing `LinkRules` gives 24 failures, and following links in `unlinkedFile` gives 6;
- the no-HTTP-scenario claim holds: the zip, tar, filesystem and git stagers all refuse a link before the reader runs.

**Why this goes back:**

1. **Procedural.** The approval was refused with `REVIEW_NOT_INDEPENDENT`, because the implementer was recorded as a bare `claude::`.
2. **D6 says "no read follows a link".** The LOW findings below are on the write path and contradict that literally. Under "fold related gaps in" they are folded here rather than filed.

**Required, folded (LOW):**

1. `PackCommitStore.copyTree` (about lines 952–967) must not follow a file link: copy with NOFOLLOW_LINKS, or refuse the link.
2. `PackCommitStore.packVersionOf` (about lines 646–647) reads `live/pack.yaml` with `readString`. Read it through the `unlinkedFile` probe, or equivalent no-follow.
3. `CommitStorePackWriteStore.registeredRelationships` (about lines 205–212): use NOFOLLOW_LINKS for `isRegularFile` and a no-follow read.
4. `FilesystemPackStore.missingNamedFile` javadoc (about lines 913–917) still says "A link is followed": correct it.
5. The configured load must name the link when `pack.yaml` itself is the link. Today it says "has no pack.yaml", against the Breaking fragment's wording.
6. The `composeRead` detail repeats the path ("the file X of qa cannot be read: X: is a symbolic link…"): state it once.
7. Optional: `DomainRepositoryService.load` uses `StoreRead.assembly`, so `Pack.violations()` lacks `pack_entry_linked`. Align it with `.of` if it is cheap; otherwise state why in the commit.

Add a test for each of 1, 2, 3 and 5 that fails before the fix. `WholePackStore.staged` reads only the service's own staging tree and needs no change.

**Procedure:**

1. Re-claim WP01 with `--agent claude --profile java-jenny`.
2. Make the changes above as signed commits.
3. Run `verifyAll -PrequireSymlinks=true`.
4. Move WP01 to `for_review` with `--agent claude::java-jenny`.

The reviewer then re-reviews the delta as `claude::reviewer-renata`.

**Recorded disposition:** the T005 contract scenario is dropped. HTTP registration cannot return `pack_entry_linked`, because every stager refuses a link first: `ZipReader:185-190`, `TarGzReader:74-77`, `LocalSourceReader:224` and `GitSourceFetcher:395-401`. Coverage is `PackRegistrationLinkTest` and `LinkedEntriesTest`.
