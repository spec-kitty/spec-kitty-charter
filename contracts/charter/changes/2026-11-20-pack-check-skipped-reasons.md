### Changed

- `PackCheckReport.skipped` names every reason `CLOSURE` is skipped: a pack whose composition facts cannot be read, a
  malformed `pack.yaml` among them (a `pack_version`, `depends_on` range or `requires_core` that is not PEP 440), or a
  pack whose level is unknown (#624). Description only; the schema is unchanged.
