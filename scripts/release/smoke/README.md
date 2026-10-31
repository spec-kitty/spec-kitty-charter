# The release smoke check's configuration root

`config-root/` is the configuration root `scripts/release/smoke-service.sh` starts every built artifact with: one
pack, `smoke`, with one tactic, so a started service loads a pack, validates it against its schema and serves it
(ADR-0046 decision 5). It is not a pack anyone activates.
