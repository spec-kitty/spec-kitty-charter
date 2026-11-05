---
title: "ADR-0061: A deployment of the charter service runs exactly one replica for now"
description: "One replica per deployment: pack writes are serialized inside one JVM, so a second replica could lose a write. More replicas wait until a hosting option is chosen."
doc_status: active
updated: 2026-11-05T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-05'
type: ADR
kind: ADR
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0026-write-surface-is-functional-registration
  - adr/0033-local-active-charter-is-source-of-truth
  - adr/0034-local-and-hosted-deployment-options
  - adr/0041-containerized-configuration-model
  - guides/run-in-a-container
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding state the service holds in memory, or anything that assumes a second instance.
  - profile: governance-lead
    guidance_text: Read this for why a containerized charter service runs one instance, and what changes that.
---

# ADR-0061: A deployment of the charter service runs exactly one replica for now

## Status

**Accepted**, 2026-11-05. Decider: Stijn Dejongh (operator), in the ruling on
[#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233) under epic
[#226](https://github.com/spec-kitty/spec-kitty-charter/issues/226). #233 asked for a spike and an ADR on the
multi-instance state model. The operator answered with a scope cut: one replica now, and the model is decided when a
hosting option is chosen. The ruling names the activation copy of ADR-0033 and the ETags of ADR-0018. The Context
below states what in the service, as built, makes a second replica unsafe.

## Context

[ADR-0041](/adr/0041-containerized-configuration-model/) makes the service run in a platform-neutral container and
leaves running more than one instance to #233. The operator's rulings on #226 (2026-10-27) say:

- One instance may serve many projects, so the state the service holds must be keyed per project.
- Tenancy is undecided. The hosting platform is not chosen: docker compose proves the container, and Kubernetes or a
  PaaS may come later.

Two kinds of state make a second process over the same files unsafe:

- **Pack writes are serialized in one process only.** A registration takes the pack's write lock, checks `If-Match`
  against the pack's revision, and commits the files by copy and swap
  (`PackWriteLocks`; see [one writer per pack](/architecture/service-lifecycle/#one-writer-per-pack)).
  [ADR-0026](/adr/0026-write-surface-is-functional-registration/) covers `If-Match` only. The lock lives in the JVM. A second replica
  over the same configuration root could pass the same `If-Match` between that check and the commit, so one of two
  writes would be lost without a `412`. The revision itself is a digest of the committed files, so both replicas
  compute the same `ETag`: the conditional request is only as safe as the single writer behind it. That is the
  service lifecycle's [one writer per pack](/architecture/service-lifecycle/#one-writer-per-pack) rule.

  A replica that only serves reads is a writer too: a listing can repair an interrupted commit by renaming
  directories, and on a read-only mount the repair just fails ([ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/)).
- **The active charter's service-side copy is not built yet** ([ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/);
  the activation operations answer `501`). ADR-0038 reserves `activations/` for its documents. Once it is built, the
  same one-writer rule applies, and anything a replica holds in memory would not see another replica's write.

Shared storage, a lock across processes or sticky routing would fix that. Each depends on the platform, and there
is no platform yet.

## Decision

1. **A deployment runs exactly one replica.** A deployment is one container (or process) serving one configuration
   root. The container guide and the image's documentation
   ([#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234)) say "single replica".
2. **More than one replica is unsupported.** A second process over the same configuration root is unsupported
   too, as the guide already says ("one container per configuration root",
   [#349](https://github.com/spec-kitty/spec-kitty-charter/issues/349)). Nothing in the service detects a second
   replica yet. The rule is documented, not enforced.
3. **Nothing is shared across processes now.** The write lock stays in the JVM. How the activation copy is stored and
   keyed is decided when it is built ([#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125)), and it
   assumes a single writer.
4. **This is revisited when a hosting option is chosen.** At that point a record decides:
   - a lock across processes, shared storage or sticky routing for pack writes and the activation copy;
   - source-fingerprint and cache coherence (`projectConfigDir`);
   - auth and session state;
   - readiness, liveness and draining;
   - the tenancy model.

   These are #233's remaining scope items, deferred to [#396](https://github.com/spec-kitty/spec-kitty-charter/issues/396).

## Consequences

### Positive

- No new infrastructure, and no change to the state model of ADR-0033 or ADR-0018.
- The health check of ADR-0041 decision 7, once built ([#232](https://github.com/spec-kitty/spec-kitty-charter/issues/232)), is enough for one replica: it will say whether the process is up. It is a
  liveness answer only. There is no readiness gate and no draining, because no second replica takes the traffic.

### Negative

- No horizontal scaling and no zero-downtime rolling deploy: a redeploy is a short outage.
- An operator could still start two replicas. Only the documentation stops them until the guard of [#349](https://github.com/spec-kitty/spec-kitty-charter/issues/349) is built.

### Risks

- A platform default, such as two replicas in a Kubernetes Deployment, is used unchanged. Mitigation: the guide says
  "single replica" next to the compose example. The future image documentation repeats it.
  A rolling update also overlaps the old and new process, even at one replica. A redeploy must stop the old process
  before starting the new one (Kubernetes: `strategy: Recreate`).

## Alternatives considered

### Sticky routing now

Not chosen. It depends on the platform's load balancer and loses the copy on failover. Two replicas over one
configuration root would still write the same packs, so it does not serialize pack writes either.

### Shared storage now

Not chosen. It means choosing a store or a lock across processes, and changing the write path (`PackWriteLocks`) and the
state model of ADR-0033, before the hosting option they must fit is known.

## References

- [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/),
  [ADR-0026](/adr/0026-write-surface-is-functional-registration/),
  [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/),
  [ADR-0034](/adr/0034-local-and-hosted-deployment-options/),
  [ADR-0041](/adr/0041-containerized-configuration-model/)
- [Run the service in a container](/guides/run-in-a-container/)
- Epic [#226](https://github.com/spec-kitty/spec-kitty-charter/issues/226), [#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233)
