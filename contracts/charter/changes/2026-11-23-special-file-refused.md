### Breaking

- A pack that holds a special file, an entry that is neither a regular file nor a directory and is not a link (a FIFO,
  a socket, a device), is now refused (ruling R-ad; ADR-0083 decision 21). Before, the walk passed over it: a
  registration accepted the pack, and its commit could block on the file; `pack-doctor validate` passed it, and
  `pack-doctor remediate` failed with exit 2. Now a storage rule refuses it as `pack_entry_linked` refuses a link, and
  nothing opens it: a registration answers `validation_failed` with the violation `unreadable` at the file's path, the
  detail `<file>: is not a regular file or a directory; a pack holds only regular files and directories`, and stores
  nothing; `pack-doctor validate` and `remediate`'s precondition answer the same violation and exit 1; the composing
  read of a configured pack holding one answers `pack_unreadable`, naming the file. At the file's path no other fault
  is stated, so a `pack.yaml` that is a FIFO is named as such, not as missing. No code is added: `unreadable` already
  answers something at the manifest's path that is not a regular file. Core's seam: none; no schema or code changes,
  and core regenerates nothing.
