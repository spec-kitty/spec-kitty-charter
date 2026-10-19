# Contract delta: charter contract 1.0.0-SNAPSHOT

The contract of record is `contracts/charter/` (ADR-0002). This file lists what this mission
changes there. It is not a second copy of the contract.

| Item | File(s) | Change | Breaking against a baseline? |
|---|---|---|---|
| `depth` | `parameters/Depth.yaml`, `schemas/ActionContext.yaml` | meaning becomes `suggests` hops; request min 0, max 2, default 2; response min 0, no max | yes (no baseline yet) |
| Walk | context path description, `ActionContext.guidance` rule | requires, suggests and vocabulary walks | behavior only |
| `hops` | `schemas/GuidanceEntry.yaml` | new required integer ≥ 1 | yes (no baseline yet) |
| `diagnostics` | `schemas/ActionContext.yaml` | new required `Diagnostic[]` | yes (no baseline yet) |
| Scope source | context path description | scope walked from the action node only | behavior only |
| `serviceVersion` | `schemas/ServiceInfo.yaml`, example | new required string | yes (no baseline yet) |
| Fingerprint inputs | `ServiceInfo.sourceFingerprint`, `headers/CharterSourceFingerprint.yaml` | adds `config.yaml` and `charter.yaml` | values only |
| Token | `openapi.yaml` `components.securitySchemes`, root `security`, `responses/Unauthorized.yaml`, `responses/Forbidden.yaml` | required bearer; 401 (with `WWW-Authenticate`) and 403 on every operation; fingerprint header on 200 only | yes (no baseline yet) |
| Refresh | `openapi.yaml`, `paths/packs_refresh.yaml` | new `POST /packs/refresh` → 200 `PackListing`, 500 refusals, 400 for a body | no (new path) |
| Refusal codes | `schemas/CharterRefusalCode.yaml` | text only: `stale_input` is raised; values unchanged | no |
| Scenarios | `scenarios/*.feature`, `scenarios/README.md` | new and retitled scenarios; `@reserved` tag rule | n/a |
