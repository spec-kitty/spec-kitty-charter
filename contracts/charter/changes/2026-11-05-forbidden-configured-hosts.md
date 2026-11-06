### Changed

- The `403` description (`responses/Forbidden.yaml` and the `charterWorktreeToken` security scheme) covers a
  containerized deployment (issue #268, ADR-0041 decision 6). There, the `Host` name is compared with the
  deployment's configured list of host names instead of the loopback names. The port is still not judged, and an
  HTTP/1.0 request without a `Host` header is refused (an HTTP/1.1 one is the servlet container's 400, as before). The
  description no longer says every 403 comes from a browser. The status, the body and the headers are unchanged. Core's seam: none.
  A local daemon still accepts only the loopback names.
