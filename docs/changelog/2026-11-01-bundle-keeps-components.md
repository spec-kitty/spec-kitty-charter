---
okf_version: "0.2"
title: The contract bundle keeps components, and the raised YAML and lint limits come down
description: charter-openapi.yaml keeps its components, shrinking from 5.2 MB to 248 KB; its readers use the default YAML limit and the lint timeout falls to 20 s.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change how the contract is bundled, linted or checked.
---

# 2026-11-01 — The contract bundle keeps components, and the raised YAML and lint limits come down

Issue [#294](https://github.com/spec-kitty/spec-kitty-charter/issues/294).

**Before:**

- `bundleContract` inlined every `$ref`, so each operation repeated its shared responses and headers. With the
  activation set (#125) the bundle grew to 5.2 MB.
- Its readers raised snakeyaml's limit from 3 MiB to 64 MiB of code points, in three places. The lint raised
  vacuum's rule timeout from 5 to 60 seconds.

**Now:**

- **The bundle keeps `components`** (248 KB). `vacuum bundle --composed` names one component after each referenced
  file and points every reference at it. `ComposedBundle` then normalizes the output:
  - it moves the path items back under `paths`;
  - it merges a file vacuum named twice (`Problem__schemas`), and refuses two different files that share a name;
  - it refuses any `$ref` that names no component of the bundle, and a component that refers back to itself, which
    the inlined bundle could not hold either;
  - it writes no YAML anchors, and quotes a string a YAML 1.1 reader would take for another type (`yes`, a date).
- **The checks read each reference as the component it names.** The closure and raised-code checks read the bundle
  through `BundleDocument`, and report a finding at the operation that uses the component. vacuum lints the bundle
  resolved, and reports a violation inside a component once, at the component.
- **Lower limits.** No reader raises snakeyaml's limit. The lint's rule timeout falls from 60 to 20 seconds, not to
  vacuum's default 5. vacuum lints the bundle resolved, so the lint only takes about half as long. On four busy
  cores a run took 6 to 7 seconds, and the default skipped rules.
- **Same API.** `oasdiff breaking` finds no change against the inlined bundle. The checks judge the same 771 response
  schemas, 42 refusal examples and 116 descriptions.
