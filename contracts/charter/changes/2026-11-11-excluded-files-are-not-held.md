### Changed

- An excluded file is not refused at registration and is not held (#451, ADR-0065 D1 as amended 2026-11-11). A file
  that a `file-kinds` exclusion names (a `README.md` beside a mission type or its templates, `plan-field-declaration.yaml`,
  a hidden file under `assets/` or `templates/`) and any Markdown file that no rule models and no artifact claims as a
  companion (an author's `docs/notes.md`) are not pack content. The registration removes them from the staged tree
  before it computes the revision and commits, so the stored pack is the registered tree less those files, and a
  registration that differs from the stored pack only in such a file ends `unchanged`. Each file it does not hold is
  reported in the answer's `diagnostics` as a `file_not_held` warning (a new `DiagnosticCode`), whose `detail` names
  the file and the rule's reason: `docs/notes.md: not held (Author aid: Markdown that no files rule models (#451).
  Not pack content: not held, not served, not refused.)`. A file nothing models that is not Markdown (`notes.txt`)
  is still refused as `unmodelled`, and the retired `pack.md` and `PROVENANCE.md` are still refused as `retired`.
  Core's seam: a registration that used to answer `422 validation_failed` for such a file now answers a 2xx with
  warnings; a client that treated the absence of `diagnostics` as "the whole tree was stored" reads `file_not_held`
  to learn what was left out; the `DiagnosticCode` enum gains `file_not_held`, so a generated client regenerates.
