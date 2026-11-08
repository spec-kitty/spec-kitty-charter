### Changed

- `PackSource.url` is `^https://\S+$`, not `^https://`: a pattern is matched against the whole value by the bean validation
  the service's generated model carries (and by any generated client that anchors it), so `^https://` accepted nothing
  but the bare prefix and refused every real URL `400`. The new pattern accepts an `https://` URL of one or more
  characters without whitespace; the host, the port, the user information and the address checks stay the fetcher's, and
  a refused one ends the registration `refused` with `422 pack_source_refused` (WP17).
