### Changed

- `pack-doctor remediate` (ADR-0083 decision 11): a pack it would not change is `unchanged` with nothing written in or
  beside it, so a canonical pack needs no write access; a pack it rewrites needs write access to the directory that
  holds it. A pack root that is a symbolic link is followed, its directory rewritten and swapped and the link kept,
  where the run failed with exit 2. A pack that is, or holds, the working directory is a usage fault (exit 2), naming
  `remediate`, as are its other usage faults. A failure says `it is unchanged` only when the pack was put back;
  otherwise standard error names the hidden `.<pack>.aside-<uuid>` directory that holds the pack as it was, then each
  suppressed failure, a failed move that is not an I/O failure worded alike. The old copy moved aside is deleted with
  its read-only directories made writable first; if it still cannot be, the pack stays rewritten, the exit code is
  unchanged, and a `pack-doctor: warning:` line names the copy to delete. On `SIGINT` or `SIGTERM` it deletes its work
  and waits for a swap under way. A pack whose modelled file the round trip refuses (a canonical form past the
  1 048 576 code-point bound, say) is `refused`, each such file named on a `the round trip refused <file>: <reason>`
  line, with nothing written and exit 1 after the check's full report, where the run exited 2 with nothing on standard
  output (Tier R review B4). `validate --round-trip` answers such a file with one `round_trip_red`, naming the size,
  where it also reported a `round_trip_written_unpaired` copy. Core's seam: none;
  the CLI is not part of the API core calls, and core regenerates nothing.
