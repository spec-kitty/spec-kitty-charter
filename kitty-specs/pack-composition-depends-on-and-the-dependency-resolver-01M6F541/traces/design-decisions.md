# Tracer: design-decisions

One entry per finding: `YYYY-MM-DD · actor · <text>`.

---

2026-11-03 · opencode (GLM 5.3, java-jenny) · WP02, T006 already existed in the kernel: PackQualifiedReference (commit fcf54bc9) parses the `<pack-name>::` prefix and leaves the remainder opaque. Kept it in the kernel instead of re-implementing or moving it into the packs application layer; the resolver dispatches URNs through the existing kernel Urn.parse, so C-005 holds without new URN-parsing code.

2026-11-03 · opencode (GLM 5.3, java-jenny) · WP02 reference resolution consumes the offering's compose, not a second resolution: OfferingReadService gained a public compose() verified read returning the closure PackCompositionResolver already resolved, under its ConsistentRead fingerprint. This was a minimal out-of-map touch (the WP file map lists OfferingReadService as owned by another concern) chosen over duplicating composition logic in the resolver, keeping the component-resolution and pack-composition graphs unmerged as the plan requires.

2026-11-03 · opencode (GLM 5.3, java-jenny) · WP02 refusal verdicts have a single authority: CompositeComponentResolver delegates both refusal cases (qualified reference to a pack outside the compose, bare reference that resolves only in another pack) to the pre-existing PackCompositionResolver.checkReferences and maps the result to CharterRefusal locally, so PACK_REFERENCE_PACK_MISSING and PACK_REFERENCE_UNQUALIFIED travel the same path as the other compose refusals. checkReferences itself was left unmodified; its static seam stays for future artifact-indexing callers.

2026-11-03 · opencode (GLM 5.3, java-jenny) · WP02 read-path wiring stops at the service seam: ArtifactReadService.referenced() mirrors item() (one verified read, outer/inner fingerprint equality, breach mapped to not-found) but no HTTP route was added — the contract surface belongs to WP03.
