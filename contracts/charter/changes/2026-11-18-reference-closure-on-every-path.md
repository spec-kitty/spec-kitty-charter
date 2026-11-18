### Breaking

- One reference closure judges every path (#355, ADR-0042 decision 7 as amended). A guard that names an asset the pack
  does not hold, an asset with no executable block or of another pack, an invalid `config_schema`, a lifecycle that
  names a missing guard or another pack's guard, a `config` its guard's schema does not fit or has no schema for, and
  a mission type whose template is missing were left out at load with `artifact_schema_error` while registration
  accepted the pack. Now:
  - `PUT /packs` (whole-pack registration) refuses such a pack: one `artifact_schema_error` at the file, in the
    load's words, among the pack's other faults in the order the load records them.
  - Validate mode reports the same finding, an error, in the same words and order.
  - A per-file write or removal that would leave a pack that does not close is refused with `409
    registration_conflict` and the load's reason, before anything is staged; removing an asset a guard names is
    refused, not cascaded. No route reaches this yet: the routes that write or remove assets, guards, lifecycles or
    templates (work-breakdown item 13) must declare the `409`.
  - The load and the whole-pack write answer as before. No refusal code is added.
- Core's seam: a client that registered a pack with a dangling guard, lifecycle, asset or template reference, or read
  validate mode as clean for it, now reads the refusal above; fix the reference in the pack.
