# Charter-offering promises: where each one lives now

<!-- cspell:ignore organisation (a contract scenario title quoted as written) -->

<!-- Pointer index for the nine promises of core's charter-offering acceptance suite. The scenarios
themselves were removed by #374; each row says what accounts for the promise now. Updated:
2026-11-05. -->

Every scenario named here is in `contracts/charter/scenarios/`. A `@contract` scenario runs in
`CharterContractSuite`; a `@WIP` scenario or a reserved pin holds the place of a surface that is not
built.

## The governance in force is what I chose

| Promise | Guarantee | Verdict | Where it lives now |
|---|---|---|---|
| `PR-CHARTER-GOVERNANCE-001` | A skill is in force only when the charter requires or selects it, not because a pack offers it. | Retired to the activation surface | `activations.feature` "Reading the effective artifacts of an activation is reserved" (501 pin, ADR-0020 D8) and `composition.feature` `@WIP` "The compose is the closure of the activated packs, not every available pack" (#360). Rule: ADR-0053 decision 10, only what the charter activates is in force; ADR-0059 D2.2. |
| `PR-CHARTER-GOVERNANCE-002` | An organization's required rule is in force on top of the project's own choices. | Retired: authority superseded | `journeys/journey-04-org-house-rules.feature` `@WIP` "A project of the organisation gets the required preset". Rule: ADR-0031, a requirement is authored as a preset. |

## The system refuses unsound governance with a clear reason

| Promise | Guarantee | Verdict | Where it lives now |
|---|---|---|---|
| `PR-CHARTER-REFUSAL-001` | An `enhances` merge that would silently drop part of an agent profile is refused. | Gap: [#380](https://github.com/spec-kitty/spec-kitty-charter/issues/380) | `profiles.feature` `@WIP` "An agent profile enhancement that would drop fields of the profile it enhances is refused", with its provisional target answer. The service does not resolve a profile's `enhances` yet (ADR-0032). |
| `PR-CHARTER-REFUSAL-002` | A reference to a name that does not exist is refused and named. | Mapped; the rest decided against | `composition.feature` "A pack-qualified reference to an artifact the named pack does not hold is refused naming both packs", "A bare cross-pack reference is refused naming the holding pack" and the outline "A relationship whose target the compose cannot resolve is refused 422 before anything is written"; `relationships.feature` "A relationship from a mission type the pack does not hold is still a conflict". A bare `to` that no pack holds is accepted as external, by ADR-0026 decision 1.3. |
| `PR-CHARTER-REFUSAL-003` | A broken charter fails closed instead of running half-applied. | Retired to the activation surface | The service reads `charter.yaml` only as an input to the source fingerprint, never as governance; the active charter is local (ADR-0033). The activation document's refusal is reserved: `errors.feature` "activation_invalid is reserved for the activation set" and the 501 pins of `activations.feature`. |

## I can share governance across a team or project safely

| Promise | Guarantee | Verdict | Where it lives now |
|---|---|---|---|
| `PR-CHARTER-SHARING-001` | A team pack carrying an unsound profile is not reported healthy. | Wired | `offering.feature` "A configured pack holding an agent profile that fails its schema refuses the offering" (500 `pack_unreadable`). |
| `PR-CHARTER-SHARING-002` | A project's own governance wins over the team pack it adopts. | Mapped in part | `composition.feature` "An org pack and a project pack defining one artifact compose as a layer override". That the project's version is the effective one waits on the layer merge and the effective reads (ADR-0055 D1, D2); the lock that keeps an org rule is `journeys/journey-04-org-house-rules.feature` `@WIP` "A locked directive cannot be deactivated by a project" (ADR-0012); ADR-0059 D3. |
| `PR-CHARTER-SHARING-003` | A shared skill under a reserved name (`spk-`, `spec-kitty-`, `spec-kitty.`) is refused. | Retired: the schema it hung on is gone | The guarantee rested on the `skill_namespace` the org-charter schema reserved. `org-charter.yaml` is retired (#431), which closes [#381](https://github.com/spec-kitty/spec-kitty-charter/issues/381) as not planned; a reserved-name rule for skills, if wanted, is new work on the pack model. |
| `PR-CHARTER-SHARING-004` | A shared asset that reaches outside its pack is refused. | Wired | `offering.feature` "A configured pack holding an asset whose path leaves assets/ refuses the offering" (500 `pack_unreadable`), and `registration/whole-pack-registration.feature` "An archive with an asset sidecar whose path leaves assets/ is refused naming the escape" (422 `validation_failed`); the detail names the escape (#382). |
