---
title: "ADR-0038: The service keeps its files under one configuration root, a directory per domain"
description: charter.config-root holds packs/ and activations/; every registered pack lives in packs/ at its declared level, so one layout serves a local machine and a hosted store.
doc_status: active
updated: 2026-10-31T00:00:00Z
decision_status: Accepted; superseded in part by ADR-0041 (2026-10-28)
decision_date: '2026-10-27'
type: ADR
kind: ADR
related:
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0020-offering-and-activations
  - adr/0034-local-and-hosted-deployment-options
  - architecture/service-lifecycle
  - architecture/descriptors
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a storage domain or a property that names a directory.
  - profile: core-maintainer
    guidance_text: Read this for how the service is told where packs live (--configRootLocation), and what replaced charter.packs.
---

# ADR-0038: The service keeps its files under one configuration root, a directory per domain

## Status

**Accepted** — 2026-10-27. Decider: Stijn Dejongh (operator). Proposed earlier the same day from
the operator's rulings given while
[#187](https://github.com/spec-kitty/spec-kitty-charter/issues/187) was closed out. Builds on
[ADR-0034](/adr/0034-local-and-hosted-deployment-options/) (local and hosted options). It
supersedes in part [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)
(the fail-closed-daemon origin) and, specifically, [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)
decision 11 as amended 2026-10-24, where they refuse every read for a configured pack whose directory is
missing (decision 4 below).

Superseded in part by [ADR-0041](/adr/0041-containerized-configuration-model/) (2026-10-28), under the container profile only: a configuration root that does not exist fails the start (decision 4).

Amended 2026-10-31 ([#174](https://github.com/spec-kitty/spec-kitty-charter/issues/174), operator): a
symbolic link under `packs/` to a directory is a pack. A pack linked from outside the configuration
root is read-only through the service, and one linked from inside it is committed through its real
target. This reverses the part of decision 3 that refused links. See
[Amendment 2026-10-31](#amendment-2026-10-31-a-linked-pack-is-a-pack). Extended the same day
([#285](https://github.com/spec-kitty/spec-kitty-charter/issues/285), operator): a `packs/` that is
itself a link is followed under the same rules, and every write path, not only the commit, keeps a
read-only pack read-only ([#287](https://github.com/spec-kitty/spec-kitty-charter/issues/287)). See
[A linked pack domain](#a-linked-pack-domain-and-every-write-path-285-287).
Extended again ([#281](https://github.com/spec-kitty/spec-kitty-charter/issues/281)): a commit that a
crash interrupted between its swap's two renames is repaired on the next listing of `packs/`, for a
plain and a linked pack. See
[The interrupted commit is repaired on the listing](#the-interrupted-commit-is-repaired-on-the-listing-281).

## Context

Until now the deployable bound a list of packs, each with a name, a level and a path
(`charter.packs[*]`), and the storage layer received it through a wiring class. Nothing grouped the
service's files: a pack could live anywhere, and the activation documents of
[ADR-0020](/adr/0020-offering-and-activations/) had no home yet. ADR-0034 asks for one service that
runs on a developer's machine and on a hosted server; both need the files in one place that can be
a local directory, a shared drive or, later, a database behind the same ports.

## Decision

1. **One configuration root.** The storage layer's own property `charter.config-root`
   (`StorageProperties`, bound in `StorageConfiguration`) names the one directory the service keeps
   its files under. `application.yaml` defaults it to `${user.home}/.spec-kitty/charter`.
   `--configRootLocation <path>` (or `--configRootLocation=<path>`) overrides it, as does any Spring
   property source. A relative path is fixed against the working directory at startup.
2. **A directory per domain.**

   ```text
   <root>/
     packs/<pack>/          every registered pack: built-in, org and project
     activations/<id>/      the activation documents, keyed by their opaque activationId (ADR-0020)
   ```

   Each domain has one subdirectory, so a domain can move to another store without touching the
   others. `activations/` is reserved: no activation API is served yet.
3. **A pack is named after its directory, at the level it declares.** The level is the
   `pack_level` of the pack's own `pack.yaml`, so project-tier content stays a registered pack of
   level `project` (ADR-0020 D11). A pack without a readable declaration is an org pack, and the pack
   policy reports what is wrong with it. Hidden directories, a commit's `<name>.pack-old-*` aside copy
   and symbolic links are not packs: a commit stages a copy and swaps it in, which on a link would
   replace the link with a copy holding only the written file.
   *(Amended 2026-10-31: a link to a directory is a pack, read-only when it is linked from outside
   the configuration root; see
   [the amendment](#amendment-2026-10-31-a-linked-pack-is-a-pack).)*
4. **Nothing is created; a missing pack is simply not served.** The service creates no directory. A
   root or a domain directory that does not exist yet holds nothing; a pack directory that is
   removed stops being listed on the next read. A root that names something other than a directory,
   or an empty value, fails the start, naming the property. The fail-closed rule of ADR-0011 and
   ADR-0018 decision 11 (2026-10-24 amendment) for a *configured* pack whose directory is missing no
   longer has a case: packs are discovered, not configured.
   *(Superseded in part by ADR-0041, 2026-10-28, under the container profile only: the root
   must be absolute and exist, or the start fails.)*
5. **Provisioning.** Packs arrive through the registration of new packs once it is served. Until
   then a pack is provisioned by copying it into `<root>/packs/`.
6. **Retired properties fail loudly.** `charter.packs[*]` and `charter.storage.*` are no longer read;
   a start that still sets one fails with a message that names `charter.config-root`, rather than
   serving no packs.
7. **One test variant.** Tests that need a whole configuration root import the deployable's
   `ConfigRootTestConfiguration`: a `@Primary` root copied from the repository's
   `src/test/resources/config-root` into a temporary directory. The production configuration's pack
   locations follow whichever root is primary.

## Consequences

### Positive

- One layout for the local and the hosted option: a user keeps all their packs and activations in one
  directory; a host keeps them on a shared drive or behind a database.
- The read path and the store take their packs from one bean, so they cannot disagree.
- The worktree id derives from the configuration root, not from which packs exist at startup.

### Negative

- A pack can no longer live outside the root, and its listed name is always its directory name.
  *(Amended 2026-10-31: a link under `packs/` can name a pack that lives elsewhere; the listed name is
  the link's.)*
- Removing a pack directory is no longer an error: the pack silently stops being served.
- Merge order among org packs follows their directory names (built-in first, then by name), not a
  configured order.

### Risks

- A launcher that still passes `charter.packs` fails at start. Mitigation: decision 6 names the
  replacement.

## Amendment 2026-10-31: a linked pack is a pack

Operator ruling on [#174](https://github.com/spec-kitty/spec-kitty-charter/issues/174). It reverses
the part of decision 3 that refused symbolic links.

- **A link to a directory is a pack.** It is named after the link, not its target, at the level the
  target's `pack.yaml` declares. The store reads it through the link, and the walk of the pack's
  files starts at the root's real path, so a linked root is no longer an empty pack. Links *below*
  a pack's root are still not followed.
- **A pack linked from outside the configuration root is read-only through the service.** It is
  read and digested wherever its target is, but `PackCommitStore` refuses a commit to it with
  `PACK_UNREADABLE` ("is linked from outside the configuration root; it is read-only through the
  service") before anything is staged. The per-file writes of `DomainRepositoryService` refuse it
  with the same message (#287). The service does not replace a directory it does not own,
  such as a pack author's checkout. Edit such a pack where it lives.
- **Inside the configuration root, a commit goes through the real target.** For a link whose target
  is inside the configuration root (in practice, in a hidden directory of `packs/`, since any other
  target there is refused below), `PackCommitStore` stages its copy beside the real target and swaps
  the target. The link stays a link, and the target holds the new content. The rename is atomic
  there, as it is for a plain pack.
- **Some entries are still not packs.** Each of these is logged as a warning once per reason,
  naming the entry and the reason, and again if it is refused after it was served:
  - a link that does not resolve, or that resolves to a file;
  - a link to the pack domain or to a directory above it;
  - a link into the configuration root outside the pack domain, such as `activations/`;
  - two or more entries whose directories overlap: one directory, or one inside the other. This
    covers two links to one target, a link to a sibling pack, and a link into a sibling pack. A
    commit to the outer pack would copy and replace the inner one's files, and the per-pack write
    lock would not serialize the two, so none of them is served (ADR-0026's atomic commit holds
    only for packs that do not overlap).
- **A linked pack domain is followed** (#285). This replaces the original line, "`<root>/packs`
  itself is still not followed when it is a link". See
  [below](#a-linked-pack-domain-and-every-write-path-285-287).

### A linked pack domain, and every write path (#285, #287)

Operator ruling on [#285](https://github.com/spec-kitty/spec-kitty-charter/issues/285), 2026-10-31.
Before it, a `<root>/packs` that was itself a link listed no pack and logged nothing, so the service
served an empty pack set and gave no reason.

- **A `packs/` that is a link is followed.** Its entries are listed through the link, under the rules
  above, checked against real paths: the domain's real directory and the configuration root's.
- **Linked from outside the configuration root, every pack in it is read-only.** Each pack's real
  directory is outside the configuration root, so every write is refused as for a single pack linked
  from outside. Linked to a directory inside the configuration root, outside the activations domain,
  its packs stay writable.
- **The overlap refusal is unchanged.** It compares the entries' real directories.
- **Some domain links hold no packs.** A `packs/` that resolves to no directory, to the
  configuration root or a directory above it, or into `activations/`, lists nothing. It is logged as
  a warning once per reason, and again if it breaks after it was fixed. Listing the configuration
  root as the pack domain would serve `activations/` and the domain itself as packs; listing
  `activations/` would serve, and write, each activation directory as a pack. The refusal of
  `activations/` is the rule for a single pack link into the configuration root outside `packs/`,
  applied to the domain (pre-PR review of #285).
- **A pack link that holds the configuration root is not a pack.** In a domain linked from elsewhere,
  the configuration root is no longer above the domain, so this is checked on its own.
- **Every write into a configured pack checks.** The per-file writes of `DomainRepositoryService`
  ask `Locations.whyReadOnly` before they write, as `PackCommitStore` does (#287): the typed saves,
  the descriptor save, and the whole-pack gate's asset, preset and pack-document writes. They are
  checked rather than narrowed to the gate, because six of them implement the public domain ports
  (`TacticRepository.save` and its siblings). `WholePackStore.write`, which creates a new pack
  directory and has no production caller, does not check; a caller that wires it into `packs/` must
  check first.

Alternative considered for #285: **warn once that a linked `packs/` is not read** (not chosen). It is
smaller, but the service would still look empty, with only a log line to explain why. It would also
refuse, for a whole domain, the layout the amendment already accepts for one pack.

### Consequences of the amendment

- A pack checkout can be linked in and served in place, but registrations into it are refused:
  it changes only where it lives.
- A commit to a pack linked from inside the configuration root replaces the *target* directory with
  its staged copy, as it replaces a plain pack's directory.
- **Crash window.** A crash between the swap's two renames leaves the live directory missing (for a
  linked pack, its target), with the `<name>.pack-old-*` aside copy beside it (for a linked pack,
  named after the target). A linked pack's link then dangles. The next listing of `packs/` repairs it
  ([#281](https://github.com/spec-kitty/spec-kitty-charter/issues/281), below), so it is no longer
  restored by hand.
- **Residual race.** A link pointed elsewhere between the listing and a commit's own resolution is
  committed to its new target without the listing's checks, the read-only check aside, which the
  commit makes on the target it resolves. A per-file write (#287) checks the real path, then writes
  through the link, so a link pointed elsewhere in between is written without the check. Whoever can
  do either can already write to `packs/`, so both are accepted.

### The interrupted commit is repaired on the listing (#281)

Decided 2026-10-31 in [#281](https://github.com/spec-kitty/spec-kitty-charter/issues/281) under the
rulings above. Before it, the repair ran only after a write had resolved the pack, which a pack in
that state never did, so the pack silently stopped being served.

- **Every listing of `packs/` looks for an interrupted commit.** There are two cases:
  - **A plain pack:** a `<name>.pack-old-*` directory with no `<name>` entry.
  - **A linked pack:** a link that does not resolve. Its chain is followed hop by hop with
    `Files.readSymbolicLink`, each hop resolved against the real directory that holds the link, to
    the missing target.
- **The repair runs under the pack's write lock** (`PackWriteLocks`), the lock a registration holds,
  and checks again once the lock is held. It moves a sole aside copy back into place and lists
  again. Only that one aside copy is moved, and nothing is created, so decision 4 holds. The
  interrupted commit was never acknowledged, so the restored state is the last one acknowledged.
- **A read can therefore rename directories in the pack domain.** A replica that shares the
  configuration root read-only is still a writer here; a read-only mount only makes the repair fail,
  which is warned.
- **A read that meets the half-swapped state is served the restored pack.**
  - A listing that reads the domain while a commit in the same process is between its renames waits
    for that pack's lock and is served the commit's result. It waits at most ten seconds, then skips
    the pack for that listing.
  - A listing that read the domain just before the first rename can still miss the pack once, as
    before.
  - A listing made while it already holds another pack's write lock only tries the lock, and skips
    the pack if the lock is held, so two packs cannot each wait on the other.
- **Not every half-swapped pack is repaired.** Nothing is guessed in these cases:
  - zero aside copies;
  - several aside copies, which is warned once with the instruction to restore one by hand;
  - a restore target outside the pack domain's real directory;
  - a restore target in or around another listed pack's directory (the overlap rule, applied before
    the move, so a repair never changes another pack's files without its lock);
  - a pack that is read-only through the service. The service never commits outside the
    configuration root, so an aside copy there is not its own.

  Such a pack stays unserved, and a dangling link is refused as before.
- **A restore that fails is warned once** and the pack is skipped. It does not fail the read.
- **A stale aside copy is hidden, never restored.** An aside copy beside a pack that exists is
  residue of a swap that finished: a crash after the second rename, or a delete that failed. Left
  as `.pack-old-*`, it would be restored if the pack were later removed on purpose. It is therefore
  renamed to a hidden `.pack-discard-*`, not deleted:
  - by a successful commit, before the commit deletes its own aside copy;
  - by the next commit to that pack, beside its real directory, which covers a linked pack;
  - by the next listing, for a plain pack.

  A copy that cannot be hidden is left whole, not half-deleted under the name the repair trusts.
- **Accepted residuals:**
  - **A crash between the second rename and the hiding.** A crash at that instant, followed by the
    pack's removal before any listing or commit, still restores the older copy.
  - **Another process.** Another process's listing during this process's swap can restore the old
    copy. This process's second rename then fails and the commit is refused, unacknowledged: the
    state after a crash. The write lock never serialized processes (ADR-0026).
  - **An empty directory made in the window.** An empty directory created at the restore path
    between the check and the move is replaced by it, because `rename(2)` replaces an empty
    directory and Java has no `RENAME_NOREPLACE`. Whoever can do that can already write to `packs/`.
  - **A link chain through an outside hop.** A chain that leaves the configuration root and comes
    back is judged by its final real path, as a commit already is.

Alternatives considered:

- **Repair only before a write** (not chosen). Reads would answer `PACK_NOT_FOUND` until something
  wrote, and a client that reads first would never write.
- **Serve the aside copy read-only** (not chosen). An aside copy is not a pack (decision 3), and its
  name is not the pack's.

### Alternatives considered for linked packs

- **Commit through any target** (built first, not chosen). Each commit would replace a directory
  the service does not own: with a new inode owned by the service's user, so its author could lose
  write access; with everything in it, `.git` included, copied on every commit; and with staging
  and aside copies written beside it.
- **Admit only links inside the configuration root** (not chosen). It would refuse to serve a
  linked checkout at all, which is what #174 asked for.

## Alternatives considered

### A list of configured packs (`charter.packs[*]`)

Not chosen: each pack is wired by hand, and there is no home for the other domains.

### One property per domain (a packs root and an activations root)

Not chosen for now: one root keeps local setup to one path. A domain can still move behind its own
port later.

## References

- [ADR-0020](/adr/0020-offering-and-activations/) D11 (project-tier content as a registered pack)
- [ADR-0034](/adr/0034-local-and-hosted-deployment-options/)
- [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) (the whole-pack seam this gate drives)
- [Service lifecycle](/architecture/service-lifecycle/)
