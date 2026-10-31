# Contract: service release assets

The interface core's pin model codes against (`kernel/service_pin.py`, `services/vendor.py`). ADR-0046 is its
durable home; this copy is the mission's test oracle.

`<ver>` is the tag without `service-v`. Tag `service-v0.2.0-rc.1` gives `<ver>` = `0.2.0-rc.1`.

| Asset | Content |
|---|---|
| `spec-kitty-charter-service-<ver>-linux-x64` | GraalVM native executable (raw, not archived) |
| `spec-kitty-charter-service-<ver>-linux-arm64` | GraalVM native executable |
| `spec-kitty-charter-service-<ver>-macos-arm64` | GraalVM native executable |
| `spec-kitty-charter-service-<ver>-windows-x64.exe` | GraalVM native executable |
| `spec-kitty-charter-service-<ver>.jar` | Spring Boot executable jar (fallback; Java 25) |
| `<asset>.sha256` | One per asset above: `<64 lowercase hex>  <asset>` |
| `SHA256SUMS` | The five assets above (not the `.sha256` files), `sha256sum` format, bare names, sorted with `LC_ALL=C` |

Rules:

- The tag matches `^service-v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$`, is not `service-v0.0.0`, and its
  version equals the build's `version`.
- A `-` suffix makes a GitHub pre-release. No release is marked latest.
- Every asset except the checksum files has a build-provenance attestation, verifiable with
  `gh attestation verify <asset> --repo spec-kitty/spec-kitty-charter --signer-workflow
  spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml --source-ref refs/tags/<tag>`.
- No other file is attached. In particular, no `contract-bundles-*`, `contract-tools-*` or
  `conformance-fixtures-*` archive: the contract releases on `contract-charter-v*`.
- Renaming or removing an asset needs a new ADR; adding a target is additive.
