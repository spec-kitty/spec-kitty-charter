---
okf_version: "0.2"
title: Two depends_on ranges that meet between post-releases overlap
description: Pep440Range.overlaps finds the development release just below a post-release, so >1.0.post0 and <1.0.post1 share 1.0.post1.dev0 and composition no longer refuses them as disjoint.
doc_status: active
updated: 2026-11-21T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0081-pack-uninstall-and-the-version-order
audience:
  - profile: pack-author
    guidance_text: Read this if composition refused two depends_on ranges on one pack as satisfiable by no version, and one of them names a post-release.
---

# 2026-11-21 — Two depends_on ranges that meet between post-releases overlap

**A post-release's own development releases count** (#386). `Pep440Range.overlaps` decides whether two ranges share a
version by trying the versions at and around each operand. It never tried the lowest development release of a
post-release operand, so `>1.0.post0` and `<1.0.post1` (which share `1.0.post1.dev0`), and `>1.0` and `<1.0.post0`
(which share `1.0.post0.dev0`), were judged disjoint, and pack composition refused two packs that asked for one
dependency in those ranges with `pack_dependency_out_of_range`.

Now an operand with a post segment also contributes `pre.post.dev0`. This check is deliberately conservative: strict
PEP 440 (`packaging` 26.3) still calls both pairs disjoint, because of its caveats on `<V` and `>V` that the charter
does not model, but the charter reports the possible overlap rather than refuse a pair that some version satisfies
under its own ordering. A test crosses every operator with operands that carry pre, post, development, epoch and local
segments, and asserts that `overlaps` holds whenever a probe version satisfies both single-clause ranges.
