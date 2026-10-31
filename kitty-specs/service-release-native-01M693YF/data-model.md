# Data model: service release line

| Entity | Fields | Rules |
|---|---|---|
| Release tag | `service-v<ver>` | `<ver>` is SemVer `X.Y.Z[-pre]`, pre-release identifiers `[0-9A-Za-z.]` only; never `0.0.0`; equals `gradle.properties` `version` |
| Release kind | final, pre-release | pre-release iff `<ver>` has a `-` suffix; a final release requires `contract-charter-v<bundled contract version>` merged into the tagged commit |
| Bundled contract version | `info.version` of `contracts/charter/openapi.yaml` at the tagged commit | reported by `GET /info` as `contractVersion`; named in the release notes; a `-SNAPSHOT` only in a pre-release |
| Target | `linux-x64`, `linux-arm64`, `macos-arm64`, `windows-x64` | core's `ServiceTarget` names; `.exe` suffix on windows-x64 |
| Release asset | name, sha256, attestation | names in [contracts/service-release-assets.md](contracts/service-release-assets.md); the set is exact |

State transitions of a release run: `tag pushed → gate passed → built (6 jobs) → assembled → published`. Any
failure before `published` leaves no release; `published` is never replaced (an existing release fails the run).
