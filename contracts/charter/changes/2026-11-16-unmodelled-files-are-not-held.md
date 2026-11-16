### Breaking

- A file no pack file kind models is not refused at registration and is not held (#554, operator ruling Q1). Pack
  authors may keep docs and reference files beside the pack content that do not affect the service, so such a file,
  Markdown or not (`notes.txt`, a `LICENSE`, a spreadsheet, a stray `charter.yaml` or a shard under `drg/` the service
  does not read), is warned about, then ignored. The registration removes it from the staged tree before it computes
  the revision and commits, as it removes an excluded file (#451), so the stored pack and its revision are those of
  the pack without it, and the round trip never sees it. The answer's `diagnostics` report it as a `file_not_held`
  warning whose `detail` names the file and the reason: `notes.txt: not held (no pack file kind models this file)`.
  The files not held are one path-sorted list, excluded and unmodelled files merged, after the load's own warnings.
  The violation code `unmodelled` is no longer answered; a retired file is still refused as `retired`, and an orphan
  prompt fragment as `prompt_fragment_invalid`. A pack whose only other fault was such a file is now judged on its
  manifest, so an uploaded `pack-manifest.yaml` whose lists differ from the pack is refused as `manifest_membership`
  where the refusal used to name only the unmodelled file. Core's seam: a registration that used to answer `422
  validation_failed` with an `unmodelled` violation now answers a 2xx with a `file_not_held` warning; a client that
  matched `unmodelled` drops it and reads `file_not_held` to learn what was left out.
