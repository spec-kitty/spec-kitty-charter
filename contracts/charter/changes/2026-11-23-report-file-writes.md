### Changed

- `pack-doctor validate --report <file>` refuses a report file inside a pack it checks, by real path with links
  followed, as a usage fault (exit 2), so the report never becomes part of a pack. The file is written to a hidden
  sibling before anything reaches standard output and renamed into place once standard output took the report: when
  standard output cannot be written, the file is left as it was, where it held a report whose `exit_code` the run did
  not exit with. A report
  file that cannot be written exits 2 with nothing on standard output; a rename that fails after standard output took
  the report exits 2 with the report there. Core's seam: none; the CLI and its report are not part of the API core
  calls, and core regenerates nothing.
