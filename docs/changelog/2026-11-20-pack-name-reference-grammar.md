---
okf_version: "0.2"
title: The pack name of a reference is a pack id
description: A qualified reference names its pack by a pack id, at most 63 characters, so a reference to a pack no pack id can name is refused when it is read; the descriptor name follows.
doc_status: active
updated: 2026-11-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - architecture/descriptors
  - guides/compose-packs
audience:
  - profile: contract-consumer
    guidance_text: Read this for the reference and pack-name patterns that changed in the charter contract.
  - profile: pack-author
    guidance_text: "Read this if a pack name or a <pack>:: qualifier you wrote is refused."
---

# 2026-11-20 — The pack name of a reference is a pack id

**A qualified reference names its pack by a pack id** (#582,
[ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/) decision 2). A pack id is a kebab
slug: lower-case letters and digits in hyphen-separated runs, at most 63 characters. Before this change, the `<pack>::`
part of a reference took `[a-z0-9][a-z0-9_-]*`, so `a_b::directive:D1` was accepted and then failed later as an
unresolved reference, because no pack can be registered as `a_b`.

## What changed

- **Refused when read.** A reference whose pack name has `_`, a leading, trailing or doubled hyphen, or more than 63
  characters is refused where it is parsed. The refusal names the pack-id grammar. This holds for a graph edge, a
  term's `see_also`, a substep candidate, a guard's `implemented_by` and a lifecycle guard `ref`.
- **The descriptor `name` is a pack id.** A pack's `name` is the `<name>::` of the references to it, and a source
  registration already required it to equal the id the pack is registered under. The schema now says so: 1 to 63
  characters, with no `_`.
- **The contracts follow.** The charter contract and the packs contract record the change under `### Breaking`:
  - the reference patterns;
  - `PackRegistration.name` and `RequiringPack`;
  - the descriptor `name`;
  - the lifecycle guard `ref`, now capped at 135 characters.
- **The cap is the service's.** A pattern cannot cap the qualifier without lookahead, so a schema accepts a 64-character
  pack name and the service refuses it.

This supersedes the 64-character cap on a guard's `<pack>::` qualifier that
[the pack id length caps](/changelog/2026-11-09-pack-id-length-caps/) recorded: the qualifier is now a pack id of at
most 63 characters.

The spec-kitty-open-packs packs are unaffected: all 24 pack names and 607 qualified references are pack ids.
