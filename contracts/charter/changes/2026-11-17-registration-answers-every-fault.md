### Breaking

- A registration of a pack whose `pack.yaml` is missing or cannot be read answers every fault of the pack, not that
  one refusal (#554, operator ruling S8; R2 rows 22 to 24). The descriptor's `pack_unreadable` at `pack.yaml` follows
  the graph faults and comes before the pack's other faults, and the problem `detail` is the one of every refused
  registration, `The pack cannot be registered: N fault(s), the first at <file>.`, where it was `The pack cannot be
  registered: <the descriptor's fault>`. Validate mode answered the same faults already. 38 golden rows change: every
  planted pack without a `pack.yaml`, and `c9-pack-version-missing-at-the-org-level`,
  `control-retired-pack-key-is-a-warning`, `control-unknown-pack-key-is-a-warning` and `pack-yaml-tilde-is-null`.
- The faults only the store's assembly met are faults of the one read, so registration and validate mode answer each
  once, in one wording (#554, A-M1; R2 rows 16 to 19 and 32):
  - Validate mode now answers a template read twice (`duplicate_artifact_id`), a value-object breach and a reference
    that does not close (`artifact_schema_error`), as the registration does (rows 16 to 18).
  - A policy file that `mutability_policy` names and that is missing or cannot be read is `unreadable`, `<file>: named
    by mutability_policy, the file is missing` or `the file cannot be read: <why>`, on both surfaces; validate mode
    answered nothing for a missing one and the policy file's own parse or schema fault for a broken one (row 32).
  - A prompt fragment that cannot be read (blank, with frontmatter, a codec fault) is one `prompt_fragment_invalid` at
    the step enhancement that names it, as `prompt-fragment-refusals.feature` pins; validate mode answered it at the
    fragment, a fault per finding (row 19). One exception: when the store refuses the enhancement itself (its id is
    not its file stem, or it breaks its schema), it reads none of its fragments, and a fragment that cannot be read is
    answered at the fragment, one fault per finding, beside the enhancement's own fault.
  - Any other companion (a guide, a skill body, a step prompt) is answered at the companion's own file, as validate
    mode answered it: in the reader's code and words when the reader reads it (`companion` for a file that does not
    parse, `artifact_schema_error` for one that breaks its structure schema), and `companion`, `<companion>: missing`
    or `<companion>: is not valid UTF-8`, when only the store meets it. The registration answered each at the artifact
    that names it, `companion`, `<artifact>: <companion path>: <why>` (row 19).
  - A graph edge that joins an artifact to itself through the pack's own name is `unreadable` at pointer `pack` on both
    surfaces; validate mode answered nothing.
- The read faults at `pack-manifest.yaml` and at the named policy file are listed after the pack's rule faults and
  before its orphan prompt fragments, as the registration listed them (WP07 review MINOR-3).
