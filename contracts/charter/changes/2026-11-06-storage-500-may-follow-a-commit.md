### Changed

- `replaceActivation` and `deleteActivation` say that a storage `500` may follow a committed change: the new document
  is stored, or the old one is gone, when the device refuses to force the directory. Read before retrying. The service
  now fails loud on a refused directory force instead of ignoring every failure to force; only a directory that cannot
  be opened (Windows) or is gone is tolerated.
