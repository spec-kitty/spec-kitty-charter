### Breaking

- A root file that differs only in letter case from a root rule's file (`Pack.yaml` beside `pack.yaml`, `Readme.md`) is
  refused by registration and validate mode under the new violation code `case_variant`, no longer `retired` (#624,
  ADR-0073 decision 3). The `validation_failed` refusal and its pointer are unchanged. Core's seam: a reader that
  matches a case variant's violation by its code matches `case_variant`.
