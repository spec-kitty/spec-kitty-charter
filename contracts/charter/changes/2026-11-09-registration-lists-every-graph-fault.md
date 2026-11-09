### Breaking

- A refused registration lists every graph fault, where it listed one (operator ruling D-1, "I want all errors in a
  single report"; Refs #424). The `422 validation_failed` of `registerPack`, `putPack` and the registration status
  (`getPackRegistration`) carries one `drg_schema_error` violation per fault of a graph document that cannot be read or breaks
  its schema, each `{pointer: <pack-relative graph file>, code: drg_schema_error}` with a `detail` naming the file and
  the reason, listed with the pack's other faults in the same report. Before, the first graph fault was the only
  violation, `{pointer: pack, code: drg_schema_error}`, and the pack's other faults were not listed. A client that
  reads that single violation at `pack` reads the file from `pointer` instead, and reads every violation of the list.
  The graph faults come first, in path order, then the pack's other violations in their order.
- The descriptions of `drg_schema_error` and of `raised` in `CharterRefusalCode` say it. The contract scenario "An
  archive with two graph documents that break their schema is refused listing both at their files"
  (`whole-pack-registration.feature`) pins it.
- core: nothing while it is not a consumer (epic #186).
