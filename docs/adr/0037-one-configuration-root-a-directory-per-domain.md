---
title: "ADR-0037: The service keeps its files under one configuration root, a directory per domain"
description: charter.config-root holds packs/ and activations/; every registered pack lives in packs/ at its declared level, so one layout serves a local machine and a hosted store.
doc_status: draft
updated: 2026-10-27T00:00:00Z
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

# ADR-0037: The service keeps its files under one configuration root, a directory per domain

## Status

**Proposed** — 2026-10-27. Decider: Stijn Dejongh (operator), rulings of 2026-10-27 given while
[#187](https://github.com/spec-kitty/spec-kitty-charter/issues/187) was closed out. Builds on
[ADR-0034](/adr/0034-local-and-hosted-deployment-options/) (local and hosted options). On
acceptance it supersedes in part [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)
and [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) where they refuse every read for
a configured pack whose directory is missing (decision 4 below).

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
4. **Nothing is created; a missing pack is simply not served.** The service creates no directory. A
   root or a domain directory that does not exist yet holds nothing; a pack directory that is
   removed stops being listed on the next read. A root that names something other than a directory,
   or an empty value, fails the start, naming the property. The fail-closed rule of ADR-0011 and
   ADR-0018 for a *configured* pack whose directory is missing no longer has a case: packs are
   discovered, not configured.
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
- Removing a pack directory is no longer an error: the pack silently stops being served.
- Merge order among org packs follows their directory names (built-in first, then by name), not a
  configured order.

### Risks

- A launcher that still passes `charter.packs` fails at start. Mitigation: decision 6 names the
  replacement.

## Alternatives considered

### A list of configured packs (`charter.packs[*]`)

Not chosen: each pack is wired by hand, and there is no home for the other domains.

### One property per domain (a packs root and an activations root)

Not chosen for now: one root keeps local setup to one path. A domain can still move behind its own
port later.

## References

- [ADR-0020](/adr/0020-offering-and-activations/) D11 (project-tier content as a registered pack)
- [ADR-0034](/adr/0034-local-and-hosted-deployment-options/)
- [Service lifecycle](/architecture/service-lifecycle/)
