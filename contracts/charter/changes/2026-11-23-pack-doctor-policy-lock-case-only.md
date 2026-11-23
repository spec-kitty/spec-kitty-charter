### Changed

- `pack-doctor validate` (`CLOSURE`) now reports a policy lock (a URN key of the pack's mutability policy file) that
  names an artifact of the pack's own closure only by letter case as `pack_reference_artifact_missing`, naming the
  declaring pack then the holder, with no violation of its own, after the preset items (ruling R-y). Registration
  already refused such a pack with the same code, so a pack `validate` passes is no longer refused at registration for
  it. No code, schema or HTTP behavior changes. Core's seam: none; the CLI and its report are not part of the API core
  calls, and core regenerates nothing.
