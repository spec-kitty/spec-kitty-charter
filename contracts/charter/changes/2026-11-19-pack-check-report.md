### Added

- `PackCheckReport` (`schemas/PackCheckReport.yaml`): what the `pack-check` CLI writes on standard output, the checks
  that ran, the packs checked, a skipped check and the problems (issue #478, ADR-0075 decision 9). No operation serves
  it, so it is in no bundle and generates no DTO. Its problems are `Problem`s with the codes, titles, statuses and
  details the registration (`getPackRegistration`) answers for the same fault; a pack's problems carry the `file:` URI
  of the pack's directory as their `instance`.
