### Changed

- `pack-doctor remediate` (ADR-0083 decision 11): a pack it would not change is `unchanged` with nothing written in or
  beside it, so a canonical pack needs no write access; a pack it rewrites needs write access to the directory that
  holds it. A pack root that is a symbolic link is followed, its directory rewritten and swapped and the link kept,
  where the run failed with exit 2. A pack that is, or holds, the working directory is a usage fault (exit 2), naming
  `remediate`, as are its other usage faults. A failure says `it is unchanged` only when the pack was put back;
  otherwise standard error names the hidden `.<pack>.aside-<uuid>` directory that holds the pack as it was, then each
  suppressed failure. On `SIGINT` or `SIGTERM` it deletes its work and waits for a swap under way. Core's seam: none;
  the CLI is not part of the API core calls, and core regenerates nothing.
