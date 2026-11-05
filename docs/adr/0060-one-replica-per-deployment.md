---
title: "ADR-0060: A deployment of the charter service runs exactly one replica for now"
description: "One replica per deployment: the write-through activation copy and the ETags live in memory per project, so more replicas wait until a hosting option is chosen."
doc_status: active
updated: 2026-11-05T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-05'
type: ADR
kind: ADR
related:
  - adr/0018-action-context-read-and-seam-access-surface
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

# ADR-0060: A deployment of the charter service runs exactly one replica for now

## Status

**Accepted**, 2026-11-05. Decider: Stijn Dejongh (operator), in the ruling on
[#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233) under epic
[#226](https://github.com/spec-kitty/spec-kitty-charter/issues/226). #233 asked for a spike and an ADR on the
multi-instance state model. The operator answered with a scope cut: one replica now, and the model is decided when a
hosting option is chosen.

## Context

[ADR-0041](/adr/0041-containerized-configuration-model/) makes the service run in a platform-neutral container and
leaves running more than one instance to #233. The operator's rulings on #226 (2026-10-27) say:

- One instance may serve many projects, so the state the service holds must be keyed per project.
- Tenancy is undecided. The hosting platform is not chosen: docker compose proves the container, and Kubernetes or a
  PaaS may come later.

The service holds per-project state in memory, in the process that answers the request:

- **The write-through copy of the active charter** ([ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/)).
  A write updates the copy and the file. A read answers from the copy.
- **The ETags** ([ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)). A conditional request is
  judged against the representation this process last computed.

With two replicas behind one address:

- A write on one replica would not be seen by the other until it re-read the file.
- An `If-Match` could pass on a replica holding a stale copy, which defeats the optimistic concurrency the ETags
  exist for.
- The source fingerprint would differ between the replicas.

Shared storage or sticky routing would fix that. Either is a platform choice, and there is no platform yet.

## Decision

1. **A deployment runs exactly one replica.** A deployment is one container (or process) serving one configuration
   root. The container guide and the image's documentation
   ([#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234)) say "single replica".
2. **More than one replica is unsupported.** Neither is a second process over the same configuration root, which
   the guide already forbids ("one container per configuration root",
   [#349](https://github.com/spec-kitty/spec-kitty-charter/issues/349)). Nothing in the service detects a second
   replica. The rule is documented, not enforced.
3. **The state stays in memory, keyed per project.** Nothing is moved to shared storage or made sticky now.
4. **This is revisited when a hosting option is chosen.** At that point a record decides:
   - shared storage or sticky routing for the write-through copy and the ETags;
   - source-fingerprint and cache coherence (`projectConfigDir`);
   - auth and session state;
   - readiness, liveness and draining;
   - the tenancy model.

   These are #233's remaining scope items. They are deferred, not dropped.

## Consequences

### Positive

- No new infrastructure, and no change to the state model of ADR-0033 or ADR-0018.
- The health check of ADR-0041 decision 7 is enough for one replica: it says whether the process is up.

### Negative

- No horizontal scaling and no zero-downtime rolling deploy: a redeploy is a short outage.
- An operator could still start two replicas. Only the documentation stops them.

### Risks

- A platform default, such as two replicas in a Kubernetes Deployment, is used unchanged. Mitigation: the guide says
  "single replica" next to the compose example. The future image documentation repeats it.

## Alternatives considered

### Sticky routing now

Not chosen. It depends on the platform's load balancer and still loses the copy on failover. It also hides the stale
read rather than removing it.

### Shared storage now

Not chosen. It means choosing a store, and changing the state model of ADR-0033 and ADR-0018, before the hosting
option it must fit is known.

## References

- [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/),
  [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/),
  [ADR-0034](/adr/0034-local-and-hosted-deployment-options/),
  [ADR-0041](/adr/0041-containerized-configuration-model/)
- [Run the service in a container](/guides/run-in-a-container/)
- Epic [#226](https://github.com/spec-kitty/spec-kitty-charter/issues/226), [#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233)
