---
okf_version: "0.2"
title: Pack registration follow-ups, stream B
description: One write generation per pack, a deliberate internal error for a failing activation listing, memory pins on git, and the revision on a refused DELETE.
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - conformance/divergence-register
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the behavior changes of the pack registration follow-ups that touch the write generation, the activation listing, the git source and the removal audit.
---

# 2026-11-08 — Pack registration follow-ups, stream B

Stream B of the follow-ups to the pre-merge review of PR A (issue 418). No contract changes.

- **One write generation per pack.** A per-artifact write, a `DELETE` and a whole-pack replace all move the generation
  of the pack's configured name, whatever identifier the request used. Before, the first two used the identifier as
  given, which could be the declared `pack_id`, so the key the facts cache of the effective reads will use moved on
  some writes only.
- **A failing activation listing is an internal error, on purpose.** When the activation store cannot be listed, the
  registration impact check ends a per-artifact write, a `DELETE` or a registration job as `500 internal_error`, as a
  stored document that cannot be read back does, and logs one line saying so. No refusal code names a fault of the
  store, and none is added ([DIV-614](/conformance/divergence-register/#div-614-a-fault-of-the-activation-store-ends-a-pack-write-as-an-internal-error)).
- **Git runs with memory pins.** Every git run carries pins that bound what it inflates and maps. A fetch of a pack up
  to the 32 MiB source bound still works. Address space is not capped
  ([DIV-613](/conformance/divergence-register/#div-613-the-git-source), [Registering packs](/guides/service-build/#registering-packs)).
- **A refused `DELETE` audits the revision it found.** A `DELETE` refused with 409, 412 or 422 enters the stored
  revision it read as the revision before, where it entered an empty one. The revision after is the same, as the
  pack did not change; a refusal before the revision is read (428, 404) still enters none.
