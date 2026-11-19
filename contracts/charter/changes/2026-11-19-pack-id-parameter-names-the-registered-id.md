### Changed

- The `packId` path parameter (`PackId.yaml`) is described as the id the Charter Pack is registered under: the name
  the service's pack registry gives it, a kebab slug of at most 63 characters (ADR-0075 decision 2; #583, FR-012). It
  said "the identity of the Charter Pack, as the pack declares it", which read as the `pack.yaml`'s `pack_id` (a ULID
  the parameter's own pattern refuses). Wording only: the schema, the pattern and every answer are unchanged.
