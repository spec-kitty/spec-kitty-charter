### Added

- Scenarios (issue #374): in `offering.feature`, "A configured pack holding an agent profile that fails its schema
  refuses the offering" (`PR-CHARTER-SHARING-001`) and "A configured pack holding an asset whose path leaves assets/
  refuses the offering" (`PR-CHARTER-SHARING-004`, an escaping path whose target exists) pin the 500
  `pack_unreadable` the service already answers, with the exact detail (`pattern violated at /roles/0`;
  `payload assets/../../../.kittify/charter.yaml does not exist`, reworded by #382: see the sidecar-path-escape entry). `@WIP` without `@contract`, so not run: in the
  new `profiles.feature`, "An agent profile enhancement that would drop fields of the profile it enhances is
  refused" (`PR-CHARTER-REFUSAL-001`, issue #380), with its provisional target answer; and the pack registration
  and distribution scenarios of ADR-0005 and ADR-0019 (issue #43), moved from `src/test/resources/features/` into
  `registration/`, `trust/`, `integrity/` and `distribution/`, decided and not built. No operation, schema or
  refusal code changes; core's seam: none.
