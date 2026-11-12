### Breaking

- The pack document is `README.md` at the pack root, not `pack.md` (#451, ADR-0042 decision 8 as amended
  2026-11-11). `pack.md` and `PROVENANCE.md` at a pack's root are retired: a pack that ships either is refused at
  registration with violation code `retired`, naming the rule and its reason, and so is a root file that differs from a
  rule's file only in letter case (`Readme.md`, `Pack.md`, `PROVENANCE.MD`). The reason for `PROVENANCE.md` is that its
  content is folded into the README's provenance section. A pack already stored before this change with a `pack.md` or
  `PROVENANCE.md` at its root keeps it until it is registered again. Until then the reads of the pack's descriptor, revision, document and of one kind's files (`getPack`, `/doc`, the artifact
  listing, a directive's item) still answer; the reads that load its whole aggregate do not. The step contract item
  read answers `404 pack_not_found`, as for any pack whose aggregate cannot be read after its `pack.yaml` was read;
  a read that composes the pack with the others (`GET /offering`, the activation and effective reads) answers
  `500 pack_unreadable`, whichever pack it was asked about. No alias, no fallback reader. Core's seam: read the
  pack document from `README.md` (and, once the document read lands, from `/doc`), not from `pack.md`; a pack's
  `PROVENANCE.md` content moves into a `## Provenance` section of its README.
