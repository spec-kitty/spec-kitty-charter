---
okf_version: "0.2"
title: Named principles, primary roles and a declared fallback profile
description: Paradigms and styleguides state named principles; a profile has one role and optional alt-roles; a pack declares its locked fallback profile.
doc_status: active
updated: 2026-11-10T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0067-paradigms-and-styleguides-state-named-principles
  - adr/0068-a-pack-declares-its-fallback-profile
  - adr/0069-a-profile-has-one-primary-role-and-optional-alt-roles
audience:
  - profile: pack-author
    guidance_text: Read this for the three shapes a pack now authors, and the refusals each one brings.
---

# 2026-11-10 — Named principles, primary roles and a declared fallback profile

Three shapes that open-packs authors against
([#456](https://github.com/spec-kitty/spec-kitty-charter/issues/456),
[#457](https://github.com/spec-kitty/spec-kitty-charter/issues/457),
[#458](https://github.com/spec-kitty/spec-kitty-charter/issues/458)). The charter's model is canonical: the old shapes
are retired with no window, and open-packs follows before its pin moves.

- **Named principles** ([ADR-0067](/adr/0067-paradigms-and-styleguides-state-named-principles/)). Before: a paradigm
  had no principles, and a styleguide's were plain strings. After: both use one element, `{name, description}`; a
  paradigm's list is optional, a styleguide's required; names are unique within a list; there is no count cap.
- **One primary role** ([ADR-0069](/adr/0069-a-profile-has-one-primary-role-and-optional-alt-roles/)). Before: a
  positional `roles` list. After: `role` and optional `alt-roles`; a primary-role fit outranks every alt-role fit.
- **A declared fallback profile** ([ADR-0068](/adr/0068-a-pack-declares-its-fallback-profile/)). Before: no way to
  declare one. After: `fallback_profile` in `pack.yaml`, held in the pack's closure, locked along it, with
  `role: generic`; a registration that breaks it is refused with its own code.
