---
okf_version: "0.2"
title: A reserved operation's 501 is no longer logged as an error
description: The service logs a call to a reserved operation at DEBUG, without a stack trace; a real server error is still logged once at ERROR with its exception.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/run-in-a-container
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you alert on ERROR lines in the service log, or wonder where the log of a 501 went.
---

# 2026-11-11 — A reserved operation's 501 is not an error in the log

**A call to a reserved operation no longer writes an `ERROR` line and a stack trace to the service log**
([#406](https://github.com/spec-kitty/spec-kitty-charter/issues/406)). A reserved operation answers `501 Not Implemented`
by design (ADR-0020 D8). The client already got the right problem, but the shared problem handler logged every `5xx`
at `ERROR` with the exception's stack trace, so a client calling a reserved operation could set off an operator alert.

- **What changed.** A `501` is logged at `DEBUG`, with the request it answered and no stack trace. Only the reserved
  operation interceptor answers `501`.
- **What did not change.** The response is the same `application/problem+json` `501`. Any other server error is still
  logged once at `ERROR` with its exception, and its body still says nothing about it.
