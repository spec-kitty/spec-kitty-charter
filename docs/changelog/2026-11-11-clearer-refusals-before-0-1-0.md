---
okf_version: "0.2"
title: Parameter 400s say what was wrong, and a refused start has no stack trace
description: A parameter 400 names the parameter; a refused start is reported without a stack trace; a path outside /api/v1 points to the API root.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/run-in-a-container
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you call the service with query filters or cursors, start it by hand, or read the contract's responses.
---

# 2026-11-11 — Clearer refusals before contract 0.1.0

The contract clean-up of [#493](https://github.com/spec-kitty/spec-kitty-charter/issues/493).

- **A malformed request parameter says what was wrong**
  ([#411](https://github.com/spec-kitty/spec-kitty-charter/issues/411)). The `400` is still a `Problem` without a code,
  and its `detail` now names the parameter, the value it carried and what it accepts, on the pack writes and the
  activation writes' precondition headers too:
  `The query parameter kind is "bogus", which is not one of tactic, directive, ... or mission_step_contract.` Before,
  most routes answered a bare `{"title":"Bad Request","status":400}`, and the activation routes a validator's own
  message. The wording is the service's, the same on every route.
- **A startup refusal has no stack trace**
  ([#454](https://github.com/spec-kitty/spec-kitty-charter/issues/454)). A setting the service refuses, such as the
  worktree token passed on the command line, is reported by Spring Boot's failure report with the message that names the
  setting and the fix, without a stack trace of bean-creation frames.
- **A path outside the API points to it** ([#454](https://github.com/spec-kitty/spec-kitty-charter/issues/454)).
  `GET /` and `GET /info` (outside `/api/v1`) still answer `404`, now with the detail `This service answers under
  /api/v1/; start at GET /api/v1/.` A path inside `/api/v1` that matches nothing stays the bare `404`. The token is
  still needed, as on every path but the container's `/actuator/health`.
- **Two unused contract responses are removed**
  ([#312](https://github.com/spec-kitty/spec-kitty-charter/issues/312)). `ActionNotFound` and `NotImplemented` were
  referenced by no operation, so no bundle held them. A contract test now fails on any definition file that
  `openapi.yaml` does not reach.
