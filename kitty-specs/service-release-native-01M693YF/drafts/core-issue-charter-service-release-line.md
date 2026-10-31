# Draft core issue (for the orchestrator to file in spec-kitty/spec-kitty)

**Title:** Service pins: name spec-kitty-charter's `service-v*` release line for `charter-service`

## Context

Operator rulings 2026-10-31 (spec-kitty#6117, comment 6544221340):

- The charter service releases on its own `service-v*` tag line in spec-kitty-charter. That line is
  separate from `contract-charter-v*`.
- The service ships native for linux-x64, linux-arm64, macos-arm64 and windows-x64, with the jar as
  fallback.
- Core pins the service at GA and fetches it on first use.

spec-kitty-charter's ADR (spec-kitty-charter#241) adopts core's model unchanged:

- **Tag:** `service-vX.Y.Z[-pre]`, which matches `kernel/service_pin.py:92` `_TAG_RE`. Pre-releases use
  only `[0-9A-Za-z.]`, for example `rc.1`.
- **Assets:**
  - `spec-kitty-charter-service-<ver>-{linux-x64,linux-arm64,macos-arm64}`, raw executables;
  - `spec-kitty-charter-service-<ver>-windows-x64.exe`;
  - `spec-kitty-charter-service-<ver>.jar`;
  - `SHA256SUMS` in `sha256sum` format with bare names;
  - a build-provenance attestation for each asset.
- **No extras:** the release does not ship `contract-bundles` or `contract-tools` tarballs. The charter
  contract stays on `contract-charter-v*`.

## Requested changes (none blocks spec-kitty-charter's release)

1. **Retire "Provisional"** at `src/kernel/service_pin.py:70`. The prefix `spec-kitty-charter-service`
   (`:71`) is now spec-kitty-charter's asset name, recorded in its ADR. Only the comment changes.
2. **Per-service default repository** in `src/specify_cli/services/vendor.py:70, 333`. Today the default is
   spec-kitty-events for every service, so the first `charter-service` bump must pass
   `--repository https://github.com/spec-kitty/spec-kitty-charter`. Proposed: a default per service, with
   `charter-service` pointing to spec-kitty-charter. The alternative is to document the flag.
3. **Docs and help:** the help texts at `src/specify_cli/cli/commands/service.py:366` and `:392`, and `pins/README.md:7-17`, name only
   spec-kitty-events. They should also name spec-kitty-charter for `charter-service`.
4. **Follow-up, already noted at `vendor.py:23`:** verify the release attestation with
   `gh attestation verify <asset> --repo spec-kitty/spec-kitty-charter --signer-workflow spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml --source-ref refs/tags/<tag>`.

## Not requested

- `_TAG_RE`, `ServiceTarget`, `service_hosts` and the jar fallback (`resolution.py`) need no change.
- `src/specify_cli/services/policies.py:58` keeps `launchable=False` for `charter-service` until the seam work (spec-kitty-charter#70)
  calls the service.
