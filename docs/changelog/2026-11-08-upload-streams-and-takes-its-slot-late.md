---
okf_version: "0.2"
title: A pack upload is streamed, takes its slot late and needs about three times its size free
description: A pack upload is streamed, holds no queue slot while it arrives, checks free space at admission on both volumes, and answers its timeout.
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0065-pack-registration-replaces-the-whole-pack
  - guides/compose-packs
  - guides/run-in-a-container
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you change the upload path of a registration, the queue or the free-space check.
  - profile: pack-author
    guidance_text: Read this if a slow upload used to make other callers get 503, or you size the disk a registration needs.
---

# 2026-11-08 — A pack upload is streamed, takes its slot late and needs about three times its size free

**`PUT /packs/{packId}` no longer holds the archive in memory.** The body is copied from the request stream into an
owner-only spool file, so ten uploads at once no longer put ten 32 MiB archives, twice over, in the heap.

**A slow upload holds no slot.** The slot of the queue (two running, eight waiting) is taken when the body has been
spooled, so a few slow uploads can no longer make other callers get `503`. A full queue, a stale `If-Match` and a
declared length over 32 MiB are still answered before the body is read, and the first two are judged again when the body
is spooled. The 90 second deadline starts then. What a slow upload does hold is a connection, up to 32 MiB of staging
and one of ten places: at most ten uploads are open at once, the eleventh is refused `503` with `Retry-After` before its
body is read, and the free-space check counts the declared bytes of the uploads already open. Put a proxy with an upload
timeout in front.

**The free-space check runs at admission and covers both volumes.** A registration needs about three times its archive
free, the archive, the tree it expands to and the copy made beside the pack: 96 MiB for a full-size archive on one volume
(it was 64 MiB on the pack volume only); on two volumes, 64 MiB on the staging volume and 32 MiB on the pack volume. The
answer is the same `503 pack_storage_full`, and an upload can now get it before its body is read. A zip is read where it
was spooled instead of being copied again.

**A job the deadline ended before it started** now answers `202` with the refused status `registration_timed_out`, the
outcome the audit records. It answered `500` before.

The reasons are [ADR-0065 D8](/adr/0065-pack-registration-replaces-the-whole-pack/#d8-registration-is-asynchronous); the
operator's side is in [Run it in a container](/guides/run-in-a-container/).
